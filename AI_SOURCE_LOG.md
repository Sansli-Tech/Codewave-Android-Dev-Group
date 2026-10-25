# AI / Source Log

| Tool or source | Question | Code affected | Changes made | Verification |
|---|---|---|---|---|
| Claude | Share summary text and Sharesheet helper | share/GroupCount.java, ShareSummaryBuilder.java, ShareHelper.java | Code written with AI help. I found and fixed a duplicate paste in GroupCount.java | ShareSummaryBuilderTest written; run pending |
| Claude | Session expiry rule matching our API (401 on a normal request ends the session, 401 on login does not) | session/SessionRules.java | Rule adjusted after I read API_CONTRACT.md | SessionRulesTest written; run pending |
| Claude | Group counts and Share button on the lecturer roster | share/GroupSummaryCalculator.java, LecturerRosterActivity.java, LecturerRosterViewModel.java, activity_lecturer_roster.xml | Share button layout was mine. Claude fixed an overlap with the list and used string resources | GroupSummaryCalculatorTest written; app build and run pending |