package gov.cms.mat.cql_elm_translation.cql_translator;

import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.CqlTranslatorException;
import org.cqframework.cql.cql2elm.LibraryBuilder;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.fhir.ucum.UcumEssenceService;
import org.fhir.ucum.UcumException;
import org.fhir.ucum.UcumService;
import org.springframework.stereotype.Service;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TranslationResource {
  public enum ModelType {
    FHIR,
    QICore;
  }

  private static final MultivaluedMap<String, CqlTranslator.Options> PARAMS_TO_OPTIONS_MAP =
      new MultivaluedHashMap<>() {
        {
          putSingle("date-range-optimization", CqlTranslator.Options.EnableDateRangeOptimization);
          putSingle("annotations", CqlTranslator.Options.EnableAnnotations);
          putSingle("locators", CqlTranslator.Options.EnableLocators);
          putSingle("result-types", CqlTranslator.Options.EnableResultTypes);
          putSingle("detailed-errors", CqlTranslator.Options.EnableDetailedErrors);
          putSingle("disable-list-traversal", CqlTranslator.Options.DisableListTraversal);
          putSingle("disable-list-demotion", CqlTranslator.Options.DisableListDemotion);
          putSingle("disable-list-promotion", CqlTranslator.Options.DisableListPromotion);
          putSingle("enable-interval-demotion", CqlTranslator.Options.EnableIntervalDemotion);
          putSingle("enable-interval-promotion", CqlTranslator.Options.EnableIntervalPromotion);
          putSingle("disable-method-invocation", CqlTranslator.Options.DisableMethodInvocation);
          putSingle("require-from-keyword", CqlTranslator.Options.RequireFromKeyword);

          // Todo Do we even use these consolidated options ?
          put(
              "strict",
              Arrays.asList(
                  CqlTranslator.Options.DisableListTraversal,
                  CqlTranslator.Options.DisableListDemotion,
                  CqlTranslator.Options.DisableListPromotion,
                  CqlTranslator.Options.DisableMethodInvocation));
          put(
              "debug",
              Arrays.asList(
                  CqlTranslator.Options.EnableAnnotations,
                  CqlTranslator.Options.EnableLocators,
                  CqlTranslator.Options.EnableResultTypes));
          put(
              "mat",
              Arrays.asList(
                  CqlTranslator.Options.EnableAnnotations,
                  CqlTranslator.Options.EnableLocators,
                  CqlTranslator.Options.DisableListDemotion,
                  CqlTranslator.Options.DisableListPromotion,
                  CqlTranslator.Options.DisableMethodInvocation));
        }
      };

  private ModelManager modelManager;
  private LibraryManager libraryManager;

  static TranslationResource instance = null;

  private TranslationResource(boolean isFhir) {
    modelManager = new ModelManager();

    if (isFhir) {
      modelManager.resolveModel("FHIR", "4.0.1");
    }

    this.libraryManager = new LibraryManager(modelManager);
  }

  public static TranslationResource getInstance(boolean model) {
    instance = new TranslationResource(model);
    // returns the singleton object
    return instance;
  }

  /*sets the options and calls cql-elm-translator using MatLibrarySourceProvider,
  which helps the translator to fetch the CQL of the included libraries from HAPI FHIR Server*/
  public CqlTranslator buildTranslator(
      InputStream cqlStream, MultivaluedMap<String, String> params) {
    try {
      UcumService ucumService = null;
      LibraryBuilder.SignatureLevel signatureLevel = LibraryBuilder.SignatureLevel.None;
      List<CqlTranslator.Options> optionsList = new ArrayList<>();

      for (String key : params.keySet()) {
        if (PARAMS_TO_OPTIONS_MAP.containsKey(key) && Boolean.parseBoolean(params.getFirst(key))) {
          optionsList.addAll(PARAMS_TO_OPTIONS_MAP.get(key));
        } else if (key.equals("validate-units") && Boolean.parseBoolean(params.getFirst(key))) {
          ucumService = getUcumService();
        } else if (key.equals("signatures")) {
          signatureLevel = LibraryBuilder.SignatureLevel.valueOf(params.getFirst("signatures"));
        }
      }

      CqlTranslator.Options[] options = optionsList.toArray(new CqlTranslator.Options[0]);

      libraryManager.getLibrarySourceLoader().registerProvider(new MadieLibrarySourceProvider());

      return CqlTranslator.fromStream(
          cqlStream,
          modelManager,
          libraryManager,
          ucumService,
          CqlTranslatorException.ErrorSeverity.Error,
          signatureLevel,
          options);

    } catch (Exception e) {
      throw new TranslationFailureException("Unable to read request", e);
    }
  }

  UcumService getUcumService() throws UcumException {
    return new UcumEssenceService(
        UcumEssenceService.class.getResourceAsStream("/ucum-essence.xml"));
  }
}
