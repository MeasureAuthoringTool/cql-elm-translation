package gov.cms.mat.cql_elm_translation.exceptions;

public class ResourceNotFoundException extends RuntimeException {
  private static final String MESSAGE = "Could not find %s resource for measure: %s";

  public ResourceNotFoundException(String type, String id) {
    super(String.format(MESSAGE, type, id));
  }
}
