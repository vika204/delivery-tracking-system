
package com.delivery.webui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RegistrationPageController {

    @GetMapping("/register")
    public String registerPage() {
        return "pages/register";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "pages/login";
    }
}
