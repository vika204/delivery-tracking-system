package com.delivery.webui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CourierDashboardPageController {

    @GetMapping("/courier/dashboard")
    public String courierDashboard(Model model) {
        model.addAttribute("pageTitle", "Courier Dashboard — WEB EXPRESS");
        return "pages/courier-dashboard";
    }
}
