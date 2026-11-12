package com.delivery.webui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProfilePageController {

    @GetMapping("/profile")
    public String profilePage(Model model) {
        model.addAttribute("pageTitle", "My Profile — WEB EXPRESS");
        model.addAttribute("activePage", "profile");
        model.addAttribute("authenticated", true);
        return "pages/profile";
    }
}
