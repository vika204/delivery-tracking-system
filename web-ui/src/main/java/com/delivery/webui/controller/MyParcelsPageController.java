package com.delivery.webui.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
public class MyParcelsPageController {

    private static final List<ParcelPreview> DEMO_PARCELS = List.of(
            new ParcelPreview(
                    15,
                    "IN_TRANSIT",
                    "Kharkiv, Sumska 10",
                    "Kyiv, Khreshchatyk 12",
                    "Marta Shevchenko",
                    "+380 67 123 45 67",
                    "12 Nov 2026, 09:42",
                    "460 km",
                    "2.40 kg",
                    "40 cm",
                    "30 cm",
                    "20 cm",
                    "250 UAH",
                    "Andrii Kovalenko",
                    "Not available"
            ),
            new ParcelPreview(
                    16,
                    "DELIVERED",
                    "Kyiv, Saksahanskoho 28",
                    "Lviv, Svobody 10",
                    "Ivan Bondarenko",
                    "+380 67 222 33 44",
                    "08 Nov 2026, 13:15",
                    "540 km",
                    "1.75 kg",
                    "35 cm",
                    "25 cm",
                    "15 cm",
                    "210 UAH",
                    "Olena Melnyk",
                    "Not available"
            ),
            new ParcelPreview(
                    17,
                    "WAITING_FOR_COURIER",
                    "Dnipro, Yavornytskoho 40",
                    "Odesa, Derybasivska 5",
                    "Sofia Petrenko",
                    "+380 67 555 44 33",
                    "12 Nov 2026, 11:05",
                    "450 km",
                    "3.10 kg",
                    "45 cm",
                    "32 cm",
                    "25 cm",
                    "290 UAH",
                    "Not assigned",
                    "Not available"
            )
    );

    @GetMapping("/my-parcels")
    public String myParcels(
            @RequestParam(defaultValue = "demo") String state,
            Model model
    ) {
        boolean loading = "loading".equalsIgnoreCase(state);
        boolean error = "error".equalsIgnoreCase(state);
        boolean empty = "empty".equalsIgnoreCase(state);

        addSharedPageAttributes(model, "My parcels — WEB EXPRESS");
        model.addAttribute("parcelsLoading", loading);
        model.addAttribute("parcelsError", error);
        model.addAttribute(
                "parcels",
                loading || error || empty ? List.of() : DEMO_PARCELS
        );

        return "pages/my-parcels";
    }

    @GetMapping("/my-parcels/{shipmentId}")
    public String parcelDetails(
            @PathVariable long shipmentId,
            Model model
    ) {
        ParcelPreview parcel = DEMO_PARCELS.stream()
                .filter(candidate -> candidate.shipmentId() == shipmentId)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Shipment preview not found"
                ));

        addSharedPageAttributes(
                model,
                "Shipment #" + shipmentId + " — WEB EXPRESS"
        );
        model.addAttribute("parcel", parcel);

        return "pages/shipment-details";
    }

    private void addSharedPageAttributes(Model model, String pageTitle) {
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("activePage", "my-parcels");
        // Display-only preview; this is not an authorization check.
        model.addAttribute("authenticated", true);
    }

    public record ParcelPreview(
            long shipmentId,
            String status,
            String pickupAddress,
            String deliveryAddress,
            String recipientName,
            String recipientPhone,
            String createdAt,
            String distance,
            String weight,
            String length,
            String width,
            String height,
            String price,
            String courierName,
            String courierPhone
    ) {
        public String statusLabel() {
            return status.replace('_', ' ');
        }

        public String statusClass() {
            return switch (status) {
                case "IN_TRANSIT" -> "transit";
                case "DELIVERED" -> "delivered";
                case "WAITING_FOR_COURIER" -> "waiting";
                case "CANCELLED" -> "cancelled";
                default -> "";
            };
        }

        public boolean delivered() {
            return "DELIVERED".equals(status);
        }
    }
}
