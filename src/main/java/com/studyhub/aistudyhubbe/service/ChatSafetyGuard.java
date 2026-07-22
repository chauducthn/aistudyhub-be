package com.studyhub.aistudyhubbe.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChatSafetyGuard {

    private static final Pattern GAMBLING = Pattern.compile(
            "cá\\s*cược|đánh\\s*cược|đánh\\s*bạc|kèo\\s*cược|nhà\\s*cái|bookmaker|gambl(?:e|ing)|bet(?:ting)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern GAMBLING_RECOMMENDATION = Pattern.compile(
            "chọn\\s+(?:một\\s+)?đội|đội\\s+nào|cược\\s+(?:vào|cho)\\s+|chốt\\s+kèo|pick\\s+(?:a\\s+)?team|who\\s+should\\s+i\\s+bet|what\\s+should\\s+i\\s+bet",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ESSENTIAL_ASSET_RISK = Pattern.compile(
            "bán\\s+nhà|cầm\\s+cố|thế\\s+chấp|vay\\s+(?:tiền|nóng)|tất\\s+tay|all[- ]?in|tiền\\s+(?:học|thuê\\s+nhà|sinh\\s+hoạt)|"
                    + "sell\\s+(?:my\\s+)?house|mortgage|borrow\\s+money|rent\\s+money|tuition|life\\s+savings",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SELF_HARM = Pattern.compile(
            "tự\\s*sát|muốn\\s*chết|không\\s+muốn\\s+sống|kết\\s*liễu|suicid(?:e|al)|kill\\s+myself|end\\s+my\\s+life|"
                    + "do(?:n't|\\s+not)\\s+want\\s+to\\s+live",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SEXUAL_CONTENT = Pattern.compile(
            "18\\+|tình\\s*dục|quan\\s*hệ\\s*tình\\s*dục|khiêu\\s*dâm|truyện\\s*sex|chat\\s*sex|ảnh\\s*nóng|khỏa\\s*thân|"
                    + "porn(?:ography)?|xxx|erotic|dirty\\s*talk|sexual\\s+content|explicit\\s+sex|nude(?:s)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern EXPLICIT_AROUSAL_CONTENT = Pattern.compile(
            "18\\+|khiêu\\s*dâm|truyện\\s*sex|chat\\s*sex|ảnh\\s*nóng|porn(?:ography)?|xxx|erotic|dirty\\s*talk|nude(?:s)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SEXUAL_REQUEST = Pattern.compile(
            "viết|tạo|kể|mô\\s*tả|cho\\s+tôi|đóng\\s*vai|nhập\\s*vai|gửi|generate|write|create|describe|show|send|role[- ]?play|tell\\s+me",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SEXUAL_EXPLOITATION = Pattern.compile(
            "trẻ\\s*em|trẻ\\s*vị\\s*thành\\s*niên|người\\s*chưa\\s*thành\\s*niên|dưới\\s*18|ấu\\s*dâm|cưỡng\\s*hiếp|"
                    + "cưỡng\\s*ép|không\\s*đồng\\s*thuận|loạn\\s*luân|mua\\s*bán\\s*người|child|minor|underage|teenager|"
                    + "rape|forced|non[- ]?consensual|incest|sexual\\s+exploitation|sex\\s+trafficking",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SEXUAL_EDUCATION = Pattern.compile(
            "sức\\s*khỏe|sinh\\s*sản|giáo\\s*dục\\s*giới\\s*tính|y\\s*khoa|phòng\\s*bệnh|tránh\\s*thai|đồng\\s*thuận|pháp\\s*luật|"
                    + "phòng\\s*chống|bảo\\s*vệ|tác\\s*hại|ảnh\\s*hưởng|báo\\s*cáo|medical|health|reproductive|"
                    + "sex(?:ual)?\\s+education|contraception|pregnan(?:t|cy)|consent|legal|law|prevention|protect|report|harm|impact",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ACTIONABLE_REQUEST = Pattern.compile(
            "cách\\s+|làm\\s+sao|hướng\\s+dẫn|chỉ\\s+(?:cho\\s+)?tôi|các\\s+bước|quy\\s+trình|viết\\s+(?:mã|code)|"
                    + "how\\s+to|step[- ]?by[- ]?step|instructions?|teach\\s+me|show\\s+me|write\\s+(?:code|a\\s+script)|build\\s+me",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ILLEGAL_OR_HARMFUL_TARGET = Pattern.compile(
            "hack|bẻ\\s*khóa|xâm\\s*nhập|chiếm\\s*đoạt\\s*tài\\s*khoản|đánh\\s*cắp|trộm|cướp|phishing|malware|ransomware|"
                    + "mã\\s*độc|bom|chất\\s*nổ|chế\\s*tạo\\s*ma\\s*túy|rửa\\s*tiền|giả\\s*(?:mạo|giấy\\s*tờ)|trốn\\s*thuế|"
                    + "vượt\\s*(?:2fa|xác\\s*thực)|credential\\s*theft|steal|robbery|explosive|make\\s+drugs|money\\s+laundering|"
                    + "fake\\s+(?:id|documents?)|tax\\s+evasion|bypass\\s+(?:2fa|authentication|security)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern INHERENTLY_HARMFUL_TARGET = Pattern.compile(
            "đánh\\s*cắp|trộm|cướp|bom|chất\\s*nổ|chế\\s*tạo\\s*ma\\s*túy|rửa\\s*tiền|giả\\s*(?:mạo|giấy\\s*tờ)|trốn\\s*thuế|"
                    + "steal|robbery|explosive|make\\s+drugs|money\\s+laundering|fake\\s+(?:id|documents?)|tax\\s+evasion",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern HARMFUL_OPERATION = Pattern.compile(
            "chế\\s*tạo|lắp\\s*ráp|kích\\s*nổ|sử\\s*dụng|rửa\\s*tiền|giả\\s*mạo|trốn\\s*thuế|đánh\\s*cắp|trộm|cướp|"
                    + "make|build|assemble|detonate|use|launder|forge|tax\\s+evasion|steal|rob",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern EVASION_OR_CONCEALMENT = Pattern.compile(
            "không\\s+bị\\s+phát\\s+hiện|che\\s+giấu\\s+bằng\\s+chứng|xóa\\s+dấu\\s+vết|qua\\s+mặt\\s+(?:công\\s*an|cảnh\\s*sát)|"
                    + "né\\s+(?:điều\\s*tra|truy\\s*vết)|avoid\\s+detection|hide\\s+evidence|cover\\s+my\\s+tracks|evade\\s+(?:police|law\\s+enforcement)|"
                    + "bypass\\s+(?:a\\s+)?safeguard",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DEFENSIVE_OR_EDUCATIONAL = Pattern.compile(
            "phòng\\s+tránh|phòng\\s+chống|nhận\\s+biết|bảo\\s+vệ|báo\\s+cáo|khắc\\s+phục|kiểm\\s+thử\\s+(?:được\\s+)?ủy\\s+quyền|"
                    + "môi\\s+trường\\s+lab|ctf|pháp\\s+luật|hậu\\s+quả|tác\\s+hại|prevent|defend|protect|detect|report|remediate|"
                    + "authorized\\s+(?:test|testing)|sandbox|legal|law|consequences?|awareness|educational",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern VIETNAMESE = Pattern.compile(
            "[ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public Optional<String> responseFor(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return Optional.empty();
        }

        String normalized = prompt.trim().toLowerCase(Locale.ROOT);
        boolean vietnamese = VIETNAMESE.matcher(normalized).find();

        if (SELF_HARM.matcher(normalized).find()) {
            return Optional.of(vietnamese ? vietnameseCrisisResponse() : englishCrisisResponse());
        }

        boolean sexualContent = SEXUAL_CONTENT.matcher(normalized).find();
        boolean explicitArousalContent = EXPLICIT_AROUSAL_CONTENT.matcher(normalized).find();
        boolean sexualExploitation = SEXUAL_EXPLOITATION.matcher(normalized).find();
        boolean sexualEducation = SEXUAL_EDUCATION.matcher(normalized).find();
        boolean sexualRequest = SEXUAL_REQUEST.matcher(normalized).find();
        boolean actionableRequest = ACTIONABLE_REQUEST.matcher(normalized).find();
        if (sexualExploitation
                && ((sexualContent && sexualRequest)
                    || (actionableRequest && !sexualEducation))) {
            return Optional.of(vietnamese
                    ? vietnameseSexualSafetyResponse(true)
                    : englishSexualSafetyResponse(true));
        }
        if ((explicitArousalContent && sexualRequest)
                || (sexualContent && sexualRequest && !sexualEducation)) {
            return Optional.of(vietnamese
                    ? vietnameseSexualSafetyResponse(false)
                    : englishSexualSafetyResponse(false));
        }

        boolean defensiveOrEducational = DEFENSIVE_OR_EDUCATIONAL.matcher(normalized).find();
        boolean illegalTarget = ILLEGAL_OR_HARMFUL_TARGET.matcher(normalized).find();
        boolean inherentlyHarmfulTarget = INHERENTLY_HARMFUL_TARGET.matcher(normalized).find();
        boolean harmfulOperation = HARMFUL_OPERATION.matcher(normalized).find();
        boolean concealment = EVASION_OR_CONCEALMENT.matcher(normalized).find();
        if (concealment
                || (inherentlyHarmfulTarget && harmfulOperation && actionableRequest)
                || (illegalTarget && actionableRequest && !defensiveOrEducational)) {
            return Optional.of(vietnamese ? vietnameseIllegalResponse() : englishIllegalResponse());
        }

        boolean gambling = GAMBLING.matcher(normalized).find();
        boolean essentialAssetRisk = ESSENTIAL_ASSET_RISK.matcher(normalized).find();
        boolean asksForRecommendation = GAMBLING_RECOMMENDATION.matcher(normalized).find();
        if (gambling && (essentialAssetRisk || asksForRecommendation)) {
            return Optional.of(vietnamese
                    ? vietnameseGamblingResponse(essentialAssetRisk)
                    : englishGamblingResponse(essentialAssetRisk));
        }

        return Optional.empty();
    }

    private String vietnameseSexualSafetyResponse(boolean exploitation) {
        if (exploitation) {
            return """
                    Mình không thể tạo, mô tả hoặc hỗ trợ nội dung tình dục liên quan trẻ vị thành niên, cưỡng ép, không đồng thuận hoặc bóc lột.

                    Nếu một người có thể đang bị xâm hại hoặc gặp nguy hiểm, hãy ưu tiên đưa họ tới nơi an toàn và liên hệ cơ quan khẩn cấp, cơ quan bảo vệ trẻ em hoặc người lớn đáng tin cậy tại nơi bạn sống. Mình có thể hỗ trợ thông tin trung lập về nhận biết, phòng ngừa và báo cáo hành vi xâm hại.
                    """.trim();
        }
        return """
                Mình không thể tạo hoặc mô tả nội dung khiêu dâm/18+ mang tính kích dục.

                Mình vẫn có thể hỗ trợ bằng ngôn ngữ trung lập về sức khỏe sinh sản, quan hệ lành mạnh, sự đồng thuận, phòng tránh bệnh lây truyền hoặc quy định pháp luật liên quan.
                """.trim();
    }

    private String englishSexualSafetyResponse(boolean exploitation) {
        if (exploitation) {
            return """
                    I cannot create, describe, or assist with sexual content involving minors, coercion, non-consent, or exploitation.

                    If someone may be abused or in immediate danger, prioritize getting them to safety and contact local emergency services, child-protection services, or a trusted adult. I can provide neutral information about recognizing, preventing, and reporting abuse.
                    """.trim();
        }
        return """
                I cannot create or describe pornographic or sexually explicit content intended for arousal.

                I can still provide neutral information about reproductive health, healthy relationships, consent, sexually transmitted infection prevention, or relevant law.
                """.trim();
    }

    private String vietnameseIllegalResponse() {
        return """
                Mình không thể cung cấp hướng dẫn, mã nguồn hoặc các bước giúp thực hiện, che giấu hay né tránh điều tra đối với hành vi vi phạm pháp luật hoặc gây hại.

                Mình có thể hỗ trợ theo hướng hợp pháp và phòng thủ, chẳng hạn giải thích rủi ro, quy định pháp luật, cách bảo vệ hệ thống/tài khoản, nhận biết dấu hiệu tấn công, lưu giữ bằng chứng và báo cáo sự việc cho đơn vị có thẩm quyền.
                """.trim();
    }

    private String englishIllegalResponse() {
        return """
                I cannot provide instructions, code, or steps that enable illegal harm, conceal it, or evade investigation.

                I can help with lawful and defensive alternatives, such as explaining risks and legal requirements, protecting systems or accounts, detecting attacks, preserving evidence, and reporting an incident to the appropriate authority.
                """.trim();
    }

    private String vietnameseGamblingResponse(boolean essentialAssetRisk) {
        String opening = essentialAssetRisk
                ? "Không nên chọn đội nào. Đừng bán, cầm cố hoặc dùng nhà ở và tài sản thiết yếu để cá cược."
                : "Mình không thể chọn đội hoặc đưa ra khuyến nghị cá cược cá nhân.";
        return """
                %s Không có đội hay kèo nào bảo đảm thắng, và một dự đoán thể thao không thể bảo vệ bạn khỏi mất tiền.

                - Dừng quyết định đặt cược và không chuyển tiền hôm nay.
                - Không vay tiền, dùng tiền học, tiền thuê nhà, tiền sinh hoạt hoặc tài sản thiết yếu để cá cược.
                - Trao đổi với một người thân đáng tin cậy. Nếu khó kiểm soát việc cá cược, hãy dùng tính năng tự khóa/tự loại trừ của nền tảng và tìm dịch vụ hỗ trợ nghiện cờ bạc tại nơi bạn sống.
                - Nếu đang có nợ hoặc nguy cơ mất chỗ ở, hãy liên hệ đơn vị tư vấn tài chính hoặc tư vấn nợ uy tín.

                Mình có thể giúp bạn lập phương án bảo vệ tài chính hoặc phân tích thể thao theo hướng thông tin, nhưng không biến phân tích đó thành lời khuyên đặt cược.
                """.formatted(opening).trim();
    }

    private String englishGamblingResponse(boolean essentialAssetRisk) {
        String opening = essentialAssetRisk
                ? "Do not choose any team. Do not sell, mortgage, or risk your home or other essential assets to gamble."
                : "I cannot choose a team or give you a personalized betting recommendation.";
        return """
                %s No team or wager is guaranteed to win, and sports analysis cannot protect you from financial loss.

                - Pause the wager and do not transfer money today.
                - Do not borrow or use rent, tuition, living expenses, or essential assets for gambling.
                - Tell someone you trust. If gambling feels difficult to control, use the platform's blocking or self-exclusion tools and seek local gambling-support services.
                - If you have debt or could lose housing, contact a reputable debt or financial-counselling service.

                I can help you make a financial safety plan or discuss sports as information, but I will not turn that analysis into betting advice.
                """.formatted(opening).trim();
    }

    private String vietnameseCrisisResponse() {
        return """
                Mình rất tiếc vì bạn đang phải chịu cảm giác này. Sự an toàn của bạn quan trọng hơn mọi câu hỏi khác.

                - Nếu bạn có thể làm hại bản thân ngay lúc này, hãy gọi dịch vụ khẩn cấp tại nơi bạn sống hoặc đến khoa cấp cứu gần nhất.
                - Đừng ở một mình; hãy gọi hoặc đến gặp ngay một người bạn, người thân, giảng viên hoặc người lớn đáng tin cậy.
                - Di chuyển ra xa thuốc, vũ khí hoặc bất kỳ vật gì có thể gây hại và ở tại nơi có người khác.

                Hãy trả lời ngắn giúp mình: hiện tại bạn có đang ở nguy hiểm ngay lập tức hoặc đã chuẩn bị cách để làm hại bản thân không?
                """.trim();
    }

    private String englishCrisisResponse() {
        return """
                I am sorry you are dealing with this. Your immediate safety matters more than anything else right now.

                - If you may hurt yourself now, call your local emergency service or go to the nearest emergency department.
                - Do not stay alone; call or go to a trusted friend, family member, teacher, or other trusted adult now.
                - Move away from medication, weapons, or anything else you could use to hurt yourself, and stay where other people are present.

                Please answer briefly: are you in immediate danger right now, or have you prepared a way to hurt yourself?
                """.trim();
    }
}
