from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
SCREENSHOTS = ROOT / "docs" / "screenshots"
BASE_URL = "http://127.0.0.1:8280"


def assert_light(page, page_name: str) -> None:
    color = page.locator("body").evaluate(
        "element => getComputedStyle(element).backgroundColor"
    )
    values = color.removeprefix("rgb(").removeprefix("rgba(").removesuffix(
        ")"
    ).split(",")
    if len(values) == 4 and float(values[3]) == 0:
        return
    channels = [int(value) for value in values[:3]]
    luminance = sum(channels) / 3
    if luminance < 160:
        raise AssertionError(f"{page_name} rendered dark background: {color}")


def main() -> None:
    SCREENSHOTS.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        context = browser.new_context(
            viewport={"width": 1440, "height": 900},
            color_scheme="dark",
        )
        context.add_init_script(
            """
            localStorage.setItem('token', 'browser-smoke-token');
            localStorage.setItem(
                'user',
                JSON.stringify({username: 'browser-smoke'}));
            """
        )
        page = context.new_page()
        page.route(
            "**/api/account/me",
            lambda route: route.fulfill(
                status=200,
                content_type="application/json",
                body=(
                    '{"code":200,"user":{"username":"browser-smoke",'
                    '"email":"smoke@sysu.edu.cn","serials":[],'
                    '"join_matching":true}}'
                ),
            ),
        )
        page.route(
            "**/api/?action=query&serial=*",
            lambda route: route.fulfill(
                status=200,
                content_type="application/json",
                body=(
                    '{"code":200,"data":{"nickname":"序列号测试用户",'
                    '"sex":"女","campus":"深圳校区",'
                    '"display_self":["基本信息"]}}'
                ),
            ),
        )
        errors: list[str] = []
        page.on(
            "console",
            lambda message: errors.append(message.text)
            if message.type == "error"
            else None,
        )

        page.goto(f"{BASE_URL}/index.html", wait_until="networkidle")
        assert_light(page, "index.html")
        page.screenshot(
            path=SCREENSHOTS / "refactor-home-light.png",
            full_page=True,
        )
        if page.locator("#announcementModal").is_visible():
            page.locator("#closeAnnouncementBtn2").click()
        page.locator("#btnQuery").click()
        page.wait_for_url("**/query.html")
        assert_light(page, "query.html")
        page.locator("#serialLookupInput").fill("SYSU-SMOKE01")
        page.locator("#serialSearchForm").press("Enter")
        page.locator(".profile-card").wait_for()
        if "序列号测试用户" not in page.locator(".profile-title").inner_text():
            raise AssertionError("serial query result was not rendered")
        page.screenshot(
            path=SCREENSHOTS / "refactor-query-light.png",
            full_page=True,
        )

        page.goto(f"{BASE_URL}/dashboard.html", wait_until="networkidle")
        assert_light(page, "dashboard.html")
        if page.locator('a[href="query.html"]').count() == 0:
            raise AssertionError("dashboard.html does not link to query.html")

        browser.close()
        if errors:
            raise AssertionError("Browser console errors: " + " | ".join(errors))
        print(
            "Browser smoke passed: light theme, navigation, and serial query "
            "rendering verified."
        )


if __name__ == "__main__":
    main()
