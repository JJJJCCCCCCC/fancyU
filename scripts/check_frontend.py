import json
import re
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "frontend"
CONTRACT = ROOT / "docs" / "contracts" / "legacy-api.json"
FORBIDDEN = {
    "sysudate-data-rumzgdjkdp.cn-hangzhou.fcapp.run": "retired cloud API URL",
    "fcapp.run": "retired cloud API URL",
    "至少6位": "obsolete six-character password hint",
    'minlength="6"': "obsolete six-character password constraint",
    "length < 6": "obsolete six-character password validation",
    "默认全部公开": "unsafe default-disclosure copy",
}


class LinkParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.links: list[str] = []
        self.scripts: list[str] = []

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == "a" and values.get("href"):
            self.links.append(values["href"])
        if tag == "script" and values.get("src"):
            self.scripts.append(values["src"])


def main() -> None:
    errors: list[str] = []
    pages = sorted(FRONTEND.glob("*.html"))
    contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
    documented_actions = set(contract["rootActions"])
    documented_routes = {
        route
        for group, routes in contract.items()
        if group != "rootActions"
        for route in routes
    }
    used_actions: set[str] = set()
    used_routes: set[str] = set()
    for page in pages:
        text = page.read_text(encoding="utf-8")
        parser = LinkParser()
        parser.feed(text)
        for value, reason in FORBIDDEN.items():
            if value in text:
                errors.append(f"{page.name}: contains {reason}")
        if re.search(r"密码[^'\"]{0,20}6位|6位[^'\"]{0,20}密码", text):
            errors.append(f"{page.name}: contains obsolete password copy")
        used_actions.update(
            re.findall(r"\baction\s*:\s*['\"]([A-Za-z][A-Za-z0-9]*)", text)
        )
        used_actions.update(re.findall(r"[?&]action=([A-Za-z][A-Za-z0-9]*)", text))
        used_routes.update(
            route.split("?", 1)[0]
            for route in re.findall(
                r"(?:API|API_BASE|API_URL)\s*\+\s*['\"]"
                r"(/[A-Za-z0-9_/?&=.-]+)",
                text,
            )
        )
        used_routes.update(
            route.split("?", 1)[0]
            for route in re.findall(
                r"\$\{API(?:_BASE|_URL)?\}"
                r"(/[A-Za-z0-9_/?&=.-]+)",
                text,
            )
        )
        if "SYSUDATE_CONFIG" in text and "config.js" not in parser.scripts:
            errors.append(f"{page.name}: uses config without loading config.js")
        for link in parser.links:
            parsed = urlparse(link)
            if (
                parsed.scheme
                or link.startswith(("#", "mailto:", "javascript:"))
                or not parsed.path
            ):
                continue
            target = FRONTEND / parsed.path
            if not target.exists():
                errors.append(f"{page.name}: missing local link {parsed.path}")

    index = (FRONTEND / "index.html").read_text(encoding="utf-8")
    query = (FRONTEND / "query.html").read_text(encoding="utf-8")
    dashboard = (FRONTEND / "dashboard.html").read_text(encoding="utf-8")
    required_edges = [
        ("index.html -> query.html", "query.html" in index),
        ("query.html -> index.html", "index.html" in query),
        ("dashboard.html -> query.html", "query.html" in dashboard),
    ]
    errors.extend(name for name, present in required_edges if not present)
    errors.extend(
        f"undocumented root action: {action}"
        for action in sorted(used_actions - documented_actions)
    )
    for route in sorted(used_routes):
        if route == "/":
            continue
        if route in documented_routes:
            continue
        if any(
            "{" in documented
            and route == documented.split("{", 1)[0]
            for documented in documented_routes
        ):
            continue
        errors.append(f"undocumented API route: {route}")

    if errors:
        raise SystemExit("\n".join(errors))
    print(
        f"Checked {len(pages)} pages; links, API contracts, and configuration "
        "are valid."
    )


if __name__ == "__main__":
    main()
