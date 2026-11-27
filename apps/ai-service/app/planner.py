import re

from app.models import Intent, QueryPlan

UNSAFE_PATTERNS = (
    "忽略之前",
    "忽略以上",
    "系统提示词",
    "数据库密码",
    "执行sql",
    "drop table",
    "select *",
    "ignore all",
    "ignore previous",
    "ignore the rules",
    "export every",
    "export all",
    "dump all",
    "user email",
    "contact information",
    "导出邮箱",
    "联系方式",
    "所有照片",
)

DISTRIBUTIONS = {
    "性别": "sex",
    "男女": "sex",
    "校区": "campus",
    "校园": "campus",
    "年级": "grade",
    "mbti": "mbti",
    "星座": "constellation",
    "专业": "major",
    "恋爱目的": "relationship_goal",
    "恋爱经验": "experience",
    "未来计划": "future_plan",
    "家乡": "hometown",
    "健身": "fitness_level",
    "身材": "body_type",
    "兴趣": "interests",
}


class UnsafeRequest(ValueError):
    pass


def plan(question: str) -> QueryPlan:
    normalized = question.strip().lower()
    if any(pattern in normalized for pattern in UNSAFE_PATTERNS):
        raise UnsafeRequest("请求涉及未授权数据或指令")

    serial = re.search(
        r"(?:序列号|编号|serial|sn)[：:\s]*(sysu-[a-z0-9]+)",
        normalized,
        re.IGNORECASE,
    )
    if serial:
        return QueryPlan(
            intent=Intent.USER_DETAIL,
            parameters={"serial": serial.group(1).upper()},
        )
    if "匹配" in normalized and any(
        word in normalized for word in ("统计", "多少", "分布", "情况")
    ):
        return QueryPlan(intent=Intent.MATCHING_STATS)
    if any(
        word in normalized
        for word in ("最近注册", "新用户", "最新注册", "最近加入")
    ):
        number = re.search(r"\d+", normalized)
        limit = min(int(number.group()) if number else 10, 50)
        return QueryPlan(
            intent=Intent.RECENT_USERS,
            parameters={"limit": limit},
        )
    if any(
        word in normalized for word in ("统计", "分布", "占比", "比例")
    ):
        for keyword, field in DISTRIBUTIONS.items():
            if keyword in normalized:
                return QueryPlan(
                    intent=Intent.DISTRIBUTION,
                    parameters={"field": field},
                )
        return QueryPlan(intent=Intent.COUNT_USERS)
    if any(
        word in normalized for word in ("总数", "总用户", "一共", "多少人")
    ):
        return QueryPlan(intent=Intent.COUNT_USERS)
    if any(
        word in normalized
        for word in ("查找", "搜索", "查询", "找", "推荐", "有没有")
    ):
        return QueryPlan(
            intent=Intent.SEARCH_USERS,
            parameters=_search_parameters(normalized),
        )
    return QueryPlan(intent=Intent.COUNT_USERS)


def _search_parameters(question: str) -> dict[str, str | int]:
    parameters: dict[str, str | int] = {}
    if "女生" in question or re.search(r"找.*女", question):
        parameters["sex"] = "女"
    elif "男生" in question or re.search(r"找.*男", question):
        parameters["sex"] = "男"
    campuses = {
        "珠海": "珠海",
        "深圳": "深圳",
        "南校": "南校园",
        "北校": "北校园",
        "东校": "东校园",
        "大学城": "东校园",
    }
    for keyword, value in campuses.items():
        if keyword in question:
            parameters["campus"] = value
            break
    grade = re.search(
        r"(大一|大二|大三|大四|大五|研一|研二|研三|研0|博士)",
        question,
    )
    if grade:
        parameters["grade"] = grade.group()
    mbti = re.search(
        r"\b(intj|intp|entj|entp|infj|infp|enfj|enfp|"
        r"istj|isfj|estj|esfj|istp|isfp|estp|esfp)\b",
        question,
    )
    if mbti:
        parameters["mbti"] = mbti.group().upper()
    height = re.search(r"(\d{3})\s*[~\-到至]\s*(\d{3})", question)
    if height:
        parameters["height_min"] = int(height.group(1))
        parameters["height_max"] = int(height.group(2))
    return parameters
