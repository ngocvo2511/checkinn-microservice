Global

Files: Use UC##_<Short_Title>_Sequence_Diagram.md, UC##_<Short_Title>_Activity_Diagram.md, UC##_<Short_Title>_Business_Rules.md.
PlantUML wrapper: Start with @startuml and end with @enduml.
Step alignment: Every numbered step in sequence MUST have a corresponding step (same number) in the activity diagram and be referenced in business rules.
Method names: Sequence diagrams use exact method names from code. Verify with grep before publishing.
No fluff: Do NOT add API Gateway, extra proxies, or Repository nodes unless they exist in the code and are relevant to the use case.

Actors & Boundaries: Declare actor for users and boundary for front-end pages (e.g., boundary "Search Page\n(app/page.tsx)" as SearchPage).
Controls & Entities: Use control for controllers/services and entity for DB (e.g., entity "HotelDB" as HotelDB).
Numbered messages: Prefix each arrow label with the sequence step number to mirror activity steps (e.g., SearchPage -> HotelController: 6. hotelApi.searchHotelsByName(hotelName)).
Activation: Surround service work with activate / deactivate.
Branches: Use alt / else / end blocks for alternative flows
Sub-steps: Use lettered sub-steps for internal calls (7a, 7b) matching the same main step number.
No user-prose: Keep labels technical (methods, HTTP paths, DB queries).



Activity Diagram (user-focused, prose only)

Two lanes: Use |Customer| and |System| sections for all flows.
Numbering: Use the same step numbers as sequence diagram. Each activity step must map to the same numbered sequence step.
Prose only: Describe objectives (what happens), not implementations. Example: :6. Send search request to backend; — do NOT write HotelController.searchByName().
Control structures: Use if/else to reflect alt branches from sequence; use repeat or loop for validation and retries.
Result checks: Represent checks like “Hotels found?” as if with yes/no outcomes that map to sequence alt blocks.
End states: Clearly end with the user-visible output (e.g., display list).
Business Rules Document

Sequence: After search result return use alt Hotels found / else No hotels found / end with message arrow to user (use MSG25 or MSG26).
Activity: Use if (Hotels found?) then (Yes) / (No) and display No hotels found in the System lane (use the message code).
Checklist before finalizing a use case

Methods verified: Confirm all method/path names in the sequence with code (grep).
Numbering synced: Sequence numbers = Activity numbers = Business Rules references.
User messages: Map user-visible outcomes to message.md codes.
No extra layers: Remove API Gateway/Repository unless present and relevant.
Preview: Render PlantUML preview once (optional) to validate layout.