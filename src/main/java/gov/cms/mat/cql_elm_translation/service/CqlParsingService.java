package gov.cms.mat.cql_elm_translation.service;

import gov.cms.mat.cql_elm_translation.dto.CqlLookups;
import gov.cms.mat.cql_elm_translation.utils.cql.CQLTools;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class CqlParsingService extends CqlTooling {
  private final CqlLibraryService cqlLibraryService;

  /**
   * Parses the CQL and generates objects for all CQL Definitions and Functions found in the Main
   * and Included Libraries.
   *
   * @param cql Main Library CQL
   * @param accessToken Requesting User's Okta Bearer token
   * @return Set of all CQL Definitions and Functions in the main and included Libraries.
   */
  public Set<CQLDefinition> getAllDefinitions(String cql, String accessToken) {
    CQLTools cqlTools = parseCql(cql, accessToken, cqlLibraryService, null);
    return prepareCqlDefinitions(cqlTools);
  }

  /**
   * Parses the CQL and generates cql artifacts(including for the CQL of the included Libraries).
   * refer CQL artifacts- gov.cms.mat.cql_elm_translation.dto.CQLLookups
   *
   * @param cql- measure cql
   * @param measureExpressions- set of cql definitions used in measure groups, SDEs & RAVs
   * @param accessToken Requesting User's Okta Bearer token
   * @return CQLLookups
   */
  public CqlLookups getCqlLookups(String cql, Set<String> measureExpressions, String accessToken) {
    if(StringUtils.isBlank(cql) || CollectionUtils.isEmpty(measureExpressions)) {
      return null;
    }

    CQLTools cqlTools = parseCql(cql, accessToken, cqlLibraryService, measureExpressions);
    String name = cqlTools.getLibrary().getIdentifier().getId();
    String version = cqlTools.getLibrary().getIdentifier().getVersion();
    String model = cqlTools.getUsingProperties().getLibraryType();
    String modelVersion = cqlTools.getUsingProperties().getVersion();
    Set<String> usedDefinitions = new HashSet<>(measureExpressions);
    // measureExpressions + used definitions
    for (var entry : cqlTools.getUsedDefinitions().entrySet()) {
      usedDefinitions.add(entry.getKey());
      usedDefinitions.addAll(entry.getValue());
    }
    // all CQLDefinitions
    Set<CQLDefinition> allCqlDefinitions = prepareCqlDefinitions(cqlTools);
    // only used CQLDefinitions(measure definitions + any used by measure definitions)
    Set<CQLDefinition> usedCqlDefinition =
        getUsedCqlDefinitions(allCqlDefinitions, usedDefinitions);
    return CqlLookups.builder()
        .context("Patient")
        .library(name)
        .version(version)
        .usingModel(model)
        .usingModelVersion(modelVersion)
        .parameters(cqlTools.getUsedParameters())
        .valueSets(cqlTools.getUsedCQLValuesets())
        .codes(cqlTools.getUsedCodes())
        .definitions(usedCqlDefinition)
        .build();
  }

  /**
   * Maps the references between CQL Definitions. In other words, which CQL Definitions and
   * Functions are called by which other CQL Definition.
   *
   * <p>Note that the resulting Map will most likely not contain all CQL Definitions and Function in
   * the provided CQL, and should be used when needing to work with all CQL Definitions and/or
   * Functions.
   *
   * @param cql CQL to parse.
   * @param accessToken Application user's Okta Bearer token.
   * @return Map representation of the callstack for CQL Definitions that call at least 1 other CQL
   *     Definition and/or Function.
   *     <p>Keys: Strings of CQL Definitions that reference/call 1 or more other CQL Definitions and
   *     Functions. CQL Definitions that do not reference any other CQL Definition and/or Function
   *     will not appear as a Key.
   *     <p>Values: Set of CQL Definition Objects that are referenced in the Key CQL Definition.
   */
  public Map<String, Set<CQLDefinition>> getDefinitionCallstacks(String cql, String accessToken) {
    CQLTools cqlTools = parseCql(cql, accessToken, cqlLibraryService, null);
    Map<String, Set<String>> nodeGraph = cqlTools.getCallstack();

    Set<CQLDefinition> cqlDefinitions =
        nodeGraph.keySet().stream()
            .map(node -> parseDefinitionNode(node, cqlTools.getDefinitionContent()))
            .filter(Objects::nonNull) // mapping function will return null for non-Definition nodes
            .collect(toSet());
    // remove null key, only contains included library references
    nodeGraph.remove(null);
    // remove nodes that don't reference any other Definition
    nodeGraph.keySet().removeIf(def -> nodeGraph.get(def).isEmpty());

    Map<String, Set<CQLDefinition>> callstack = new HashMap<>();

    for (String parentDefinition : nodeGraph.keySet()) {
      Set<String> defNames = nodeGraph.get(parentDefinition);
      Set<CQLDefinition> calledDefinitions = new HashSet<>(defNames.size());

      for (String defName : defNames) {
        Optional<CQLDefinition> calledDefinition =
            cqlDefinitions.stream().filter(d -> d.getId().equals(defName)).findFirst();
        calledDefinition.ifPresent(calledDefinitions::add);
      }

      if (!calledDefinitions.isEmpty()) {
        callstack.putIfAbsent(parentDefinition, calledDefinitions);
      }
    }
    return callstack;
  }

  private Set<CQLDefinition> prepareCqlDefinitions(CQLTools cqlTools) {
    return cqlTools.getDefinitionContent().keySet().stream()
        .map(def -> parseDefinitionNode(def, cqlTools.getDefinitionContent()))
        .collect(toSet());
  }

  private Set<CQLDefinition> getUsedCqlDefinitions(
      Set<CQLDefinition> cqlDefinitions, Set<String> usedDefinitions) {
    return cqlDefinitions.stream()
        .filter(cqlDefinition -> usedDefinitions.contains(cqlDefinition.getName()))
        .map(
            cqlDefinition -> {
              String logic = cqlDefinition.getLogic();
              return cqlDefinition.toBuilder()
                  .definitionName(cqlDefinition.getName())
                  // TODO: Ideally should come from listener/visitor
                  .definitionLogic(logic.split(":", 2)[1])
                  .build();
            })
        .collect(Collectors.toSet());
  }

  private CQLDefinition parseDefinitionNode(String node, Map<String, String> cqlDefinitionContent) {
    // Graph includes retrieves, functions, and included library references.
    // Filter out any node that does not have CQL Definition text content.
    if (!cqlDefinitionContent.containsKey(node)) {
      return null;
    }

    CQLDefinition definition = new CQLDefinition();
    definition.setId(node);
    definition.setDefinitionLogic(cqlDefinitionContent.get(node));

    // Included Lib Define: AHAOverall-2.5.000|AHA|Has Left Ventricular Assist Device
    // Main Lib Define: Numerator
    String[] parts = node.split("\\|");
    if (parts.length == 1) { // Define from Main Library
      definition.setDefinitionName(node);
    } else if (parts.length >= 3) { // Define from some included Library
      definition.setLibraryDisplayName(parts[1]);
      definition.setDefinitionName(parts[2]);
      String[] libraryParts = parts[0].split("-");
      if (libraryParts.length == 2) {
        definition.setParentLibrary(libraryParts[0]);
        definition.setLibraryVersion(libraryParts[1]);
      }
    }
    // TODO could use a stronger comparator for determining if node is Definition or Function
    definition.setFunction(definition.getDefinitionLogic().startsWith("define function"));
    return definition;
  }

  public Map<String, Set<String>> getUsedFunctions(String cql, String accessToken) {
    CQLTools cqlTools = parseCql(cql, accessToken, cqlLibraryService, null);
    return cqlTools.getUsedFunctions();
  }
}
