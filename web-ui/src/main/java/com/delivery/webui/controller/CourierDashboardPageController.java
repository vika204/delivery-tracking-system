package com.delivery.webui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CourierDashboardPageController {

    @GetMapping("/courier/dashboard")
    public String courierDashboard(Model model) {
        model.addAttribute("pageTitle", "Courier Dashboard — WEB EXPRESS");
        model.addAttribute("activePage", "courier-dashboard");
        model.addAttribute("headerState", "COURIER");
        return "pages/courier-dashboard";
    }
}
