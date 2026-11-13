package com.delivery.webui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingPageController {

    @GetMapping("/")
    public String landingPage(Model model) {
        model.addAttribute("pageTitle", "WEB EXPRESS — Great Speed. Great Delivery.");
        model.addAttribute("activePage", "home");
        model.addAttribute("headerState", "GUEST");
        return "pages/landing";
    }
}
