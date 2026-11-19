package com.delivery.webui.controller.support;

import com.delivery.webui.controller.NotificationsPageController;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestClientException;

import java.util.List;

@ControllerAdvice(assignableTypes = NotificationsPageController.class)
public class NotificationsPageAdvice {

    @ModelAttribute
    void pageAttributes(Model model) {
        model.addAttribute("pageTitle", "Notifications — WEB EXPRESS");
        model.addAttribute("activePage", "notifications");
        model.addAttribute("headerState", "CLIENT");
        model.addAttribute("notificationsError", false);
    }

    @ExceptionHandler(RestClientException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    String notificationsUnavailable(Model model) {
        pageAttributes(model);
        model.addAttribute("notificationsError", true);
        model.addAttribute("notifications", List.of());
        return "pages/notifications";
    }
}
