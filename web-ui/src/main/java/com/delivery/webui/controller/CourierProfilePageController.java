package com.delivery.webui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CourierProfilePageController {

    @GetMapping("/courier/profile")
    public String courierProfilePage(Model model) {
        model.addAttribute("pageTitle", "Courier Profile — WEB EXPRESS");
        return "pages/courier-profile";
    }
}
