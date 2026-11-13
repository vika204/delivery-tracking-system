# WEB EXPRESS UI pages

All routes below are Spring MVC/Thymeleaf pages in `web-ui`. The current `headerState` values (`GUEST`, `CLIENT`, `COURIER`) only select navigation content; they are not authorization. Keycloak and Spring Security must provide the real identity and role later.

| URL | Audience | Controller | Template | Page CSS / JS | Current data and remaining integration |
|---|---|---|---|---|---|
| `/` | GUEST | `LandingPageController` | `pages/landing.html` | `landing.css`, `landing.js` | Marketing prototype; CTA shows a demo dialog. |
| `/login` | GUEST | `RegistrationPageController` | `pages/login.html` | `register.css`, `register.js` | UI only; connect form to Keycloak login. |
| `/register` | GUEST | `RegistrationPageController` | `pages/register.html` | `register.css`, `register.js` | Client/courier tabs are UI prototypes; connect both flows to Keycloak and role provisioning. KATOTTG lookup is local. |
| `/profile` | CLIENT | `ProfilePageController` | `pages/profile.html` | `profile.css`, `profile.js` | Demo profile; load and save the authenticated client. |
| `/ship` | CLIENT | `CreateShipmentPageController` | `pages/create-shipment.html` | `create-shipment.css`, `create-shipment.js` | Form and KATOTTG validation are local; connect submission, distance and price to shipment services. |
| `/ship/success/{id}` | CLIENT | `ShipmentCreatedPageController` | `pages/shipment-created.html` | `shipment-created.css` | Uses the path ID; populate confirmed shipment data from backend. |
| `/my-parcels` | CLIENT | `MyParcelsPageController` | `pages/my-parcels.html` | `my-parcels.css` | Demo list and `empty/loading/error` previews; load shipments for the authenticated client. |
| `/my-parcels/{id}` | CLIENT | `MyParcelsPageController` | `pages/shipment-details.html` | `my-parcels.css` | Demo details for known IDs; replace with an ownership-checked shipment query. |
| `/tracking` | GUEST / CLIENT | `TrackingPageController` | `pages/tracking.html` | `tracking.css`, tracking component CSS/JS | Shipment status, GPS history and live stream are integrated for local development. Secure shipment/GPS access before publication. `previewRole=CLIENT` changes only the demo header. |
| `/courier/dashboard` | COURIER | `CourierDashboardPageController` | `pages/courier-dashboard.html` | `courier-dashboard.css`, `courier-dashboard.js` | Offers, deliveries and availability are demo data; connect dispatch APIs for the authenticated courier. |
| `/courier/profile` | COURIER | `CourierProfilePageController` | `pages/courier-profile.html` | `profile.css`, `courier-profile.css`, `courier-profile.js` | Demo courier data; connect profile/work-zone persistence. |
| `/courier/deliveries/{id}` | COURIER | `CourierDeliveryDetailsPageController` | `pages/courier-delivery-details.html` | `courier-delivery-details.css`, `courier-delivery-details.js` | Demo delivery and local status controls; connect dispatch/shipment APIs with courier assignment checks. |
| any missing HTML route | GUEST | Spring Boot error handling | `error/404.html` | `error.css` | Static branded error page; retains HTTP 404. |
| internal HTML error | GUEST | Spring Boot error handling | `error/500.html` | `error.css` | Static branded error page; retains HTTP 500 and exposes no exception details. |

## Shared UI

- `fragments/header.html`, `header.css`, `header.js`: one role-aware navigation fragment and accessible profile dropdown.
- `cursor-webs.css`, `cursor-webs.js`: the original Register pointer-web algorithm, loaded once on every full page.
- `fragments/ui/button-webs.html`, `buttons.css`: reusable decorative web for primary red CTA buttons.
- `fragments/ui/modal.html`, `modal.css`, `modal.js`: reusable application modal.
- `katottg-autocomplete.css`, `katottg-autocomplete.js`: shared settlement lookup used by registration, courier profile and shipment creation.

API endpoints under `/api/tracking/**` are not pages and remain handled by their existing tracking controllers. Real access control is still required for all CLIENT/COURIER pages and local tracking endpoints.
