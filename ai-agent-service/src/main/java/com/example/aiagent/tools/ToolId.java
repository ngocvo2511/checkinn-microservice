package com.example.aiagent.tools;

/** Closed internal READ allow-list. No caller supplied route or method. */
public enum ToolId {
    A01("search_hotels", "/api/hotel/hotels/offers/page", "Search public hotels; base room/night prices, one matching room type per hotel; availability is not a hold."),
    A02("get_hotel", "/api/hotel/hotels/{hotelId}", "Read published hotel details and general policies; no private contact data."),
    A03("list_public_rooms", "/api/hotel/api/v1/hotel/{hotelId}", "List public effectively approved room types; base prices are not final quotes."),
    A04("get_public_room", "/api/hotel/api/v1/{roomTypeId}", "Resolve one public approved room type; nullable capacity remains unknown."),
    A05("check_availability", "/api/hotel/api/v1/availability/check", "Observe availability for confirmed dates and quantity; does not reserve or hold rooms."),
    A06("resolve_locations", "/api/hotel/cities/search/locations", "Resolve city/province names; ambiguous matches require clarification."),
    A07("list_accommodation_types", "/api/hotel/hotels/accommodation-types", "Read exact public accommodation codes and labels."),
    A08("list_amenity_catalog", "/api/hotel/amenity-catalog", "Read active amenity codes; catalog membership does not prove hotel amenities."),
    A09("get_review_summaries", "/api/hotel/api/v1/reviews/summaries", "Read aggregate public review counts and ratings, without reviewer identities."),
    A10("get_cancellation_policy", "/api/bookings/cancellation-policy", "Read general cancellation policy only; never booking-specific refund eligibility or amounts.");

    private final String toolName;
    private final String path;
    private final String description;
    ToolId(String toolName, String path, String description) {
        this.toolName = toolName; this.path = path; this.description = description;
    }
    public String toolName() { return toolName; }
    public String path() { return path; }
    public String description() { return description; }
    public String method() { return "GET"; }
    public static ToolId named(String name) {
        for (var id : values()) if (id.toolName.equals(name)) return id;
        throw new ToolFailure(ToolError.Category.UNSUPPORTED_TOOL);
    }
}
