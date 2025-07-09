package com.liskovsoft.smartyoutubetv2.common.app.models.data;

public class BlocklistRule {
    public enum RuleType {
        CHANNEL_ID,
        TITLE_KEYWORD,
        DESCRIPTION_KEYWORD
    }

    private final RuleType type;
    private final String value;

    public BlocklistRule(RuleType type, String value) {
        this.type = type;
        this.value = value;
    }

    public RuleType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public static BlocklistRule fromString(String ruleString) {
        if (ruleString == null || ruleString.trim().isEmpty()) {
            return null;
        }

        String[] parts = ruleString.split(":", 2);
        if (parts.length < 2) {
            // Default to title keyword if no prefix, or handle as error
            // For now, let's assume malformed lines are ignored or logged by parser
            return null;
        }

        String typeStr = parts[0].trim().toLowerCase();
        String valueStr = parts[1].trim();

        if (valueStr.isEmpty()) {
            return null;
        }

        switch (typeStr) {
            case "channel":
            case "channelid":
                return new BlocklistRule(RuleType.CHANNEL_ID, valueStr);
            case "title":
            case "titlekeyword":
                return new BlocklistRule(RuleType.TITLE_KEYWORD, valueStr);
            case "desc":
            case "description":
            case "descriptionkeyword":
                return new BlocklistRule(RuleType.DESCRIPTION_KEYWORD, valueStr);
            default:
                // Unknown rule type
                return null;
        }
    }
}
