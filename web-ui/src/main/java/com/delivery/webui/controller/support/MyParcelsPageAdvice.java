package com.delivery.webui.controller.support;

import com.delivery.webui.controller.MyParcelsPageController;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

@ControllerAdvice(assignableTypes = MyParcelsPageController.class)
public class MyParcelsPageAdvice {

    @ModelAttribute
    void pageAttributes(Model model) {
        model.addAttribute("pageTitle", "My parcels — WEB EXPRESS");
        model.addAttribute("activePage", "my-parcels");
        model.addAttribute("headerState", "CLIENT");
        model.addAttribute("parcelsError", false);
    }

    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String shipmentNotFound() {
        return "error/404";
    }

    @ExceptionHandler(RestClientException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    String shipmentsUnavailable(Model model) {
        pageAttributes(model);
        model.addAttribute("parcelsError", true);
        model.addAttribute("parcels", List.of());
        model.addAttribute("page", 0);
        model.addAttribute("hasNext", false);
        return "pages/my-parcels";
    }
}
