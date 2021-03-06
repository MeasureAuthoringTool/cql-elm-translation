package gov.cms.mat.cql_elm_translation.cql_translator;

import gov.cms.mat.cql.elements.UsingProperties;
import gov.cms.mat.cql_elm_translation.service.MadieFhirServices;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.cqframework.cql.cql2elm.LibrarySourceProvider;
import org.hl7.elm.r1.VersionedIdentifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MadieLibrarySourceProvider implements LibrarySourceProvider {
  private static final ConcurrentHashMap<String, String> cqlLibraries = new ConcurrentHashMap<>();
  private static final ThreadLocal<UsingProperties> threadLocalValue = new ThreadLocal<>();
  private static final ThreadLocal<String> threadLocalValueAccessToken = new ThreadLocal<>();
  private static MadieFhirServices madieFhirServices;

  public static void setFhirServicesService(MadieFhirServices madieFhirServices) {
    MadieLibrarySourceProvider.madieFhirServices = madieFhirServices;
  }

  public static void setUsing(UsingProperties usingProperties) {
    threadLocalValue.set(usingProperties);
  }

  public static void setAccessToken(String accessToken) {
    threadLocalValueAccessToken.set(accessToken);
  }

  private static String createKey(String name, String qdmVersion, String version) {
    return name + "-" + qdmVersion + "-" + version;
  }

  @Override
  public InputStream getLibrarySource(VersionedIdentifier libraryIdentifier) {
    String usingVersion = threadLocalValue.get().getVersion(); // using FHIR version '4.0.0
    String key = createKey(libraryIdentifier.getId(), usingVersion, libraryIdentifier.getVersion());

    if (cqlLibraries.containsKey(key)) {
      return getInputStream(cqlLibraries.get(key)); // do we need to expire cache ?????
    } else {
      return processLibrary(libraryIdentifier, key);
    }
  }

  private InputStream processLibrary(VersionedIdentifier libraryIdentifier, String key) {
    String[] supportedLibraries = new String[] {"FHIR", "QICore"};
    if (Arrays.stream(supportedLibraries)
        .anyMatch(threadLocalValue.get().getLibraryType()::contains)) {
      return getInputStream(libraryIdentifier, key);
    } else {
      throw new IllegalArgumentException(
          String.format("%s is not supported.", threadLocalValue.get().getLibraryType()));
    }
  }

  private InputStream getInputStream(VersionedIdentifier libraryIdentifier, String key) {
    String cql =
        madieFhirServices.getHapiFhirCql(
            libraryIdentifier.getId(),
            libraryIdentifier.getVersion(),
            threadLocalValueAccessToken.get());
    return processCqlFromService(key, cql);
  }

  private InputStream processCqlFromService(String key, String cql) {
    if (StringUtils.isEmpty(cql)) {
      log.debug("Did not find any cql for key : {}", key);
      return null;
    } else {
      cqlLibraries.put(key, cql);
      return getInputStream(cql);
    }
  }

  private InputStream getInputStream(String cql) {
    return IOUtils.toInputStream(cql, StandardCharsets.UTF_8);
  }
}
