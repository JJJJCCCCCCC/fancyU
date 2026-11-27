from typing import Any


def format_answer(result: dict[str, Any]) -> str:
    result_type = result.get("type")
    title = result.get("title", "查询结果")
    data = result.get("data")
    if result_type == "empty" or data == []:
        return "没有找到符合条件的用户。"
    if result_type == "count" and isinstance(data, dict):
        return (
            f"{title}：共 {data.get('total', 0)} 人，"
            f"男生 {data.get('male', 0)} 人，女生 {data.get('female', 0)} 人。"
        )
    if result_type == "table" and isinstance(data, list):
        lines = [f"{title}，共查询到 {len(data)} 条记录。"]
        for index, row in enumerate(data[:20], 1):
            lines.append(
                f"{index}. {row.get('nickname') or row.get('serial_number')}"
                f"；{row.get('sex') or '未填写'}；"
                f"{row.get('campus') or '校区未填写'}；"
                f"{row.get('grade') or '年级未填写'}"
            )
        return "\n".join(lines)
    if result_type == "detail" and isinstance(data, dict):
        fields = (
            ("昵称", "nickname"),
            ("性别", "sex"),
            ("校区", "campus"),
            ("年级", "grade"),
            ("专业", "major"),
            ("MBTI", "mbti"),
            ("兴趣", "interests"),
        )
        values = [
            f"{label}：{data[key]}"
            for label, key in fields
            if data.get(key) not in (None, "")
        ]
        return f"{title}\n" + "\n".join(values)
    if isinstance(data, list):
        lines = [title]
        for row in data:
            lines.append(f"- {row.get('value') or '未填写'}：{row.get('count', 0)}")
        return "\n".join(lines)
    if isinstance(data, dict):
        return title + "\n" + "\n".join(
            f"- {key}：{value}" for key, value in data.items()
        )
    return f"{title}：暂无可展示数据。"
