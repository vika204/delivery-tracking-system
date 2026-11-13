
package com.delivery.webui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RegistrationPageController {

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("headerState", "GUEST");
        return "pages/register";
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("headerState", "GUEST");
        return "pages/login";
    }
}
