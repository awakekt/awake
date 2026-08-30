# Skill evaluations

These fixtures define observable decisions that the web-to-Compose skill family must make. The
pytest contract test validates that every fixture is complete and that the linked skills expose
the routing and ownership rules each case needs. It runs through `scripts/awake verify` as part of
the existing `tool-tests` gate.

This is deliberately not presented as a full agent-behaviour test: a deterministic test cannot
prove that a model understood a screenshot or made a good implementation. For a material skill
revision, forward-test an unmodified fixture with an independent agent, without providing this
file's expected outcome. Compare its actual routing, ownership decision, warning, and forbidden
output with the fixture afterwards. Add a fixture only for a demonstrated failure or an important
boundary decision.

Run the deterministic contract suite directly with:

```bash
python3 -m pytest -q tools/test_skill_evals.py
```
