package gov.cms.mat.cql_elm_translation.service.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.cms.mat.cql.elements.LibraryProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import java.util.*;

@Slf4j
public class AnnotationErrorFilter implements CqlLibraryFinder, JsonHelpers {

  @Getter private final String cqlData;
  private final boolean showWarnings;
  private final String json;

  private LibraryProperties libraryProperties;
  private List<JsonNode> keeperList = new ArrayList<>();
  private List<JsonNode> externalList = new ArrayList<>();
  private ObjectMapper objectMapper = new ObjectMapper();

  /*
   * This class is similar to CqlTranslatorExceptionFilter, Here we filter out the annotation Node.
   * Verifies if the Annotation node in cqlTranslationResult is present. If present,
   * then the errors are filtered out if they are not pointed to the parent library.
   * This service also adds a new node "externalErrors" which contains a list of errors
   * that are not part of current cql library.
   * */
  // Todo Do we even need these annotations ?
  public AnnotationErrorFilter(String cqlData, boolean showWarnings, String json) {
    this.cqlData = cqlData;
    this.showWarnings = showWarnings;
    this.json = json;
  }

  public String filter() {
    try {
      JsonNode rootNode = readRootNode();

      Optional<ArrayNode> annotationNode = getAnnotationNode(rootNode);

      if (annotationNode.isEmpty()) {
        return json;
      } else {
        libraryProperties = parseLibrary();
        return processArrayNode(rootNode, annotationNode.get());
      }
    } catch (Exception e) {
      log.info("Error filtering annotations", e);
      return json;
    }
  }

  private JsonNode readRootNode() throws JsonProcessingException {
    return objectMapper.readTree(json);
  }

  private Optional<ArrayNode> getAnnotationNode(JsonNode rootNode) {
    JsonNode libraryNode = rootNode.get("library");
    JsonNode annotationNode = libraryNode.get("annotation");

    return validateArrayNode(annotationNode);
  }

  private Optional<ArrayNode> validateArrayNode(JsonNode annotationNode) {
    if (annotationNode == null || annotationNode.isMissingNode()) {
      log.info("Annotation node is missing");
      return Optional.empty();
    }

    if (annotationNode.isEmpty()) {
      log.info("Annotation node is empty");
      return Optional.empty();
    }

    if (annotationNode instanceof ArrayNode) {
      return Optional.of((ArrayNode) annotationNode);
    } else {
      log.info(
          "Annotation node is not a ArrayNode, class: {}",
          annotationNode.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  private String processArrayNode(JsonNode rootNode, ArrayNode annotationArrayNode) {
    annotationArrayNode.forEach(this::filterByNode);

    annotationArrayNode.removeAll();
    annotationArrayNode.addAll(keeperList);

    if (rootNode instanceof ObjectNode) {
      ObjectNode rootObjectNode = (ObjectNode) rootNode;

      ArrayNode arrayNode = objectMapper.createArrayNode();
      arrayNode.addAll(externalList);

      rootObjectNode.set("externalErrors", arrayNode);
    }
    return fixErrorTags(rootNode.toPrettyString());
  }

  private void filterByNode(JsonNode jsonNode) {
    //    if the json node has translationVersion
    //    clean all non translator version annotations
    //  we want to keep a node that contains translator version that's been cleaned.
    if (getTextFromNodeId(jsonNode, "translatorVersion").isPresent()) {
      JsonNode copiedNode = jsonNode.deepCopy();
      Iterator<String> fieldNames = copiedNode.fieldNames();
      while (fieldNames.hasNext()) {
        if (!Objects.equals(fieldNames.next(), "translatorVersion")) {
          fieldNames.remove();
        }
      }
      keeperList.add(jsonNode);
    }

    if (!isLibraryNodeValid(jsonNode)) {
      return;
    }

    if (!showWarnings && !isError(jsonNode)) {
      return;
    }

    if (filterNodeByLibrary(jsonNode)) {
      keeperList.add(jsonNode);
    } else {
      externalList.add(jsonNode);
    }
  }

  private boolean filterNodeByLibrary(JsonNode node) {
    Optional<String> optionalLibraryId = getLibraryId(node);
    Optional<String> optionalLibraryVersion = getLibraryVersion(node);

    if (optionalLibraryId.isEmpty() || optionalLibraryVersion.isEmpty()) {
      return false;
    } else {
      return isPointingToSameLibrary(
          libraryProperties, optionalLibraryId.get(), optionalLibraryVersion.get());
    }
  }

  private Optional<String> getLibraryId(JsonNode node) {
    return getTextFromNodeId(node, "libraryId");
  }

  private Optional<String> getLibraryVersion(JsonNode node) {
    return getTextFromNodeId(node, "libraryVersion");
  }

  private boolean isPointingToSameLibrary(LibraryProperties p, String libraryId, String version) {
    return p.getName().equals(libraryId) && p.getVersion().equals(version);
  }

  private String fixErrorTags(String cleanedJson) {
    return cleanedJson.replace("\"errorSeverity\" : \"error\"", "\"errorSeverity\" : \"Error\"");
  }

  private boolean isLibraryNodeValid(JsonNode node) {
    Optional<String> optionalLibraryId = getLibraryId(node);
    Optional<String> optionalLibraryVersion = getLibraryVersion(node);

    return optionalLibraryId.isPresent() && optionalLibraryVersion.isPresent();
  }

  private boolean isError(JsonNode node) {
    JsonNode errorSeverityNode = node.path("errorSeverity");

    var optional = getSeverityValueFromNode(errorSeverityNode);

    return optional.isPresent() && optional.get().equals("error");
  }

  private Optional<String> getSeverityValueFromNode(JsonNode errorSeverityNode) {
    var optional = getTextFromNode(errorSeverityNode);
    return optional.map(s -> s.toLowerCase().trim());
  }
}
