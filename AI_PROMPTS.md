# AI prompts

AI was used in two places:
1. **As a development assistant:** Claude Code (desktop app, model Claude Opus 5.5) helped plan, build and test this project, step by step.
2. **Inside the framework:** the optional AI failure triage sends a prompt to the Claude API (Claude Opus 5.5) when a test fails. That prompt is in the code: [`TriagePrompt.java`](runner/src/test/java/com/zipo/qa/ai/TriagePrompt.java).

## How the assistant was used

The working method, set in the first prompt and kept for the whole project:
- For each deliverable the assistant **proposed a plan**; I reviewed it, changed it where needed, and **approved it** before any work started.
- After building, the assistant **checked and tested its own output** (re-runs, deliberately broken tests, fresh-clone runs) and reported what it found and fixed.
- **Nothing was committed without my approval.** Commits stayed local and were pushed in two batches: once in Step 4 to get CI running, and once at the end.

Where my decisions changed the assistant's proposals:
- Tool: switched from the proposed Python + Appium to **Maestro + Java**.
- Scope: paywall options tested from **one row** only; the list treated as **dynamic** (non-blank, not exact names); **no separate** launch, list or paywall-opening tests; a **dedicated** paywall content test.
- Test plan: committed **first, on its own**; reshaped into a standard format, then **trimmed** back to the main points.
- Removed the rotation test and the whole known-failure mechanism; the rotation bug is documented in `BUGS.md` instead.
- Skipped the proposed device health checks and the CI failure demo.
- Separated the framework's own unit tests from the UI suite, and kept them as a gate on CI.
- Bug list: dropped two candidates and an observations section, and set severities.

## Development prompts

Only prompts and instructions are listed: setup messages and requests for explanations are left out. Spelling is corrected; wording is otherwise unchanged. Some prompts use IDs from the assistant's proposal at that moment; notes explain them where needed.

### Brief and working rules
> Now here is the task:
>
> *[pasted the assignment brief]*
>
> Read it carefully, provide a step by step plan for the each part of the task. After approving task by me - do it step by step - you need to get approval for every step. After step completion - run analyse - check your code/output for any issues, make sure it works, provide any improvements. After resolving everything for step 1 - provide plan for step 2, get my approval and so on.

> first of all - let's discuss several things:
>
> 1. don't commit anything before my approve - I want to have clear commit history
> 2. if we'll switch to Maestro + Java - how it will affect us?
> 3. If every download button have the same action called - we don't need to cover all the buttons. There are 4 states there: after click on download - Subscribe, Purchase, Restore purchases and not now - we need to check every option from this list - but only for 1 button. Expected results are different for all these cases

*Outcome: commits only after approval; the assistant compared Maestro + Java with its Appium proposal; paywall scope limited to one row with four distinct expected results.*

> Maestro + Java

### Step 1: Test plan
> Planned test cases
> Don't need to verify cold launch separately, it will be verified for an every next test
> Don't need to check the list of Earth, probably it can be dynamic - what can we do here? Like - create verification that list is not blank, or something
> PAY-01 verification is included into the -02-03 etc, we can skip it

*Outcome: launch, list and paywall-opening checks moved into shared steps run by every test; the list check became "at least one row with a non-blank name". (PAY-01 was then "paywall opens".)*

> Let's add a verification of Premium title displaying, along with existing of the text under it. We can verify it in 1 test

> First step in the commit should be test plan

> Approve Step 1

> I want this plan to be more like real test plan - provide some options

> *(chose from the options)* IEEE 829 / 29119-lite

> it's way too huge, lets make it more simple without losing a main point. remove the less necessary parts

> Yes, commit it

### Step 2: Framework and tests
> paywall - do we need to have a separated yaml for the each test?

*Outcome: one file per test (Maestro's unit of reporting), with the shared paywall logic in a parameterised subflow.*

> Approve, commit the APK and include TC-08

*(TC-08 was then the optional "Back / outside tap" test, now TC-06.)*

> cold run emulator and rerun current tests

> remove TC-06 from: test plan (if it's there), verification and code base - everywhere, we don't need it

*(TC-06 was the rotation test, which was expected to fail because of the rotation bug.)*

> Not now

> #1, skip #2 and proceed with #3

*(#1: run the 3-run stability check; #2: device health fixes, skipped; #3: commit.)*

### Step 3: Failure diagnostics
> Yes, plan Step 3

> Approve, failed tests only, full logcat

> Yes, 4 cores + rerun

> Not yet

### Step 4: CI
> step 4

> 1. trigger only on Push to main and manual run
> 2. Play store
> 3. Ok to create public repo

*(A pull request trigger was added later, in the review fixes below.)*

> no need to do failure demo on a temporary branch

> firstly lets install brew

> done, gh is installed and logged in

> done, scope added, approve both commits

> yes, separate them

*(Framework unit tests separated from the UI suite: `./gradlew test` runs only the app's UI tests.)*

> keep A

*(Option A: framework unit tests run first on CI and stop the run if they fail.)*

> we'll do the final push in the end
> approving 2 commits

### Step 5: AI in the framework
> Yes, plan Step 5

> Approve, failure triage, Opus 5.5

> Build without a key

> commit step 5

### Step 6: Bugs
> Yes, plan Step 6

> Approve, include section B, screenshots no, Low
> BUG-03 ignore
> BUG-04 medium
> BUG-08 ignore
> Observations ignore

*(IDs are from the candidate list in the plan; the final `BUGS.md` is renumbered 01-06.)*

> Yes, commit

### Step 7: README
> Yes, plan Step 7

> Approve, include badge, example message yes, rotation ok

> Yes, commit

### Step 8: AI prompts
> Yes, plan Step 8

> fixing spelling only,
> I also want to remove from the prompting general questions like "what is that and explain it to me etc" - keep only prompting, editing answers etc
> The first messages - remove

### Review fixes (after the first submission)
After an AI review of the finished repo against the brief, I picked the fixes to make and asked for them in one commit:

> fix items 2 and 3 in the repo
> add #4
> add #6
> #12 lets switch
> #13 - explain
>
> Do no commit anything yet. all these changes I want to do in 1 commit

*(#2: show the plan's "3 green runs" exit criterion is met; #3: fix inconsistencies between the documents; #4: mention the HTML test report as a CI artifact; #6: run CI on pull requests too; #12: swap TC-06 and TC-07 so the IDs follow the run order; #13: explain why the test plan changed after the test code.)*

**Why the test plan changed after the test code.** The plan was committed first, on its own, before any test code (`docs: add test plan`). It was then edited on purpose, in separate commits, when a decision changed:
- `docs: remove rotation test from test plan`: I decided not to keep the rotation test as a permanently failing test; the bug is in `BUGS.md` instead. This commit has the same timestamp as the first test commits because the plan and the code were changed in the same step.
- The review fixes commit: the coverage rule referred to "P1" risks that the plan doesn't have (now "every risk in section 3"), and TC-06 / TC-07 were swapped to match the order the tests run in.

The scope, risks and approach in the plan are otherwise unchanged from the first commit.
