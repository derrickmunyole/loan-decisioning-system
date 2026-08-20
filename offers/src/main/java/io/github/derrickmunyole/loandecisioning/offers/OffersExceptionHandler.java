package io.github.derrickmunyole.loandecisioning.offers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "io.github.derrickmunyole.loandecisioning.offers")
class OffersExceptionHandler {

    @ExceptionHandler({OfferNotFoundException.class, NoOfferForApplicationException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String handleNotFound(RuntimeException e) {
        return e.getMessage();
    }

    @ExceptionHandler({OfferNotAcceptableException.class, OfferExpiredException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    String handleConflict(RuntimeException e) {
        return e.getMessage();
    }
}
