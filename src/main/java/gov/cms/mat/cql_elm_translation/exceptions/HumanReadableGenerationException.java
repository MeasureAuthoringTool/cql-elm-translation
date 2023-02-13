package gov.cms.mat.cql_elm_translation.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class HumanReadableGenerationException extends RuntimeException {
  private static final String MESSAGE = "Could not find %s with id: %s";

  public HumanReadableGenerationException(String type, String id) {
    super(String.format(MESSAGE, type, id));
  }
}
