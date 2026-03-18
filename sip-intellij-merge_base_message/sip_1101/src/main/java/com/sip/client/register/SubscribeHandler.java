package com.sip.client.register;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

/**
 * SIP 订阅处理器
 * 专门用于处理 SUBSCRIBE/NOTIFY 相关的 PIDF 文档解析
 *
 * 功能:
 * 1. 解析 PIDF (Presence Information Data Format) XML
 * 2. 提取用户在线状态信息
 * 3. 支持标准的 RFC 3863 PIDF 格式
 *
 * PIDF 格式示例:
 * <presence xmlns="urn:ietf:params:xml:ns:pidf" entity="sip:alice@example.com">
 *   <tuple id="alice">
 *     <status>
 *       <basic>open</basic>
 *     </status>
 *     <note>online</note>
 *   </tuple>
 * </presence>
 *
 */
@Slf4j
public class SubscribeHandler {

    /**
     * 在线状态枚举
     */
    public enum PresenceStatus {
        ONLINE("online", "在线"),
        BUSY("busy", "忙碌"),
        AWAY("away", "离开"),
        OFFLINE("offline", "离线");

        private final String code;
        private final String desc;

        PresenceStatus(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public static PresenceStatus fromCode(String code) {
            for (PresenceStatus status : values()) {
                if (status.code.equalsIgnoreCase(code)) {
                    return status;
                }
            }
            return OFFLINE;
        }
    }

    /**
     * Presence 信息封装类
     */
    public static class PresenceInfo {
        private String entity;        // SIP URI
        private String basicStatus;   // basic: open/closed
        private String note;           // 详细状态: online/busy/away/offline
        private PresenceStatus status; // 枚举状态

        public PresenceInfo(String entity, String basicStatus, String note) {
            this.entity = entity;
            this.basicStatus = basicStatus;
            this.note = note;
            this.status = PresenceStatus.fromCode(note);
        }

        public String getEntity() {
            return entity;
        }

        public String getBasicStatus() {
            return basicStatus;
        }

        public String getNote() {
            return note;
        }

        public PresenceStatus getStatus() {
            return status;
        }

        @Override
        public String toString() {
            return "PresenceInfo{" +
                    "entity='" + entity + '\'' +
                    ", basicStatus='" + basicStatus + '\'' +
                    ", note='" + note + '\'' +
                    ", status=" + status +
                    '}';
        }
    }

    /**
     * 解析 PIDF XML 文档
     *
     * @param pidfXml PIDF XML 字符串
     * @return PresenceInfo 对象
     */
    public PresenceInfo processPidf(String pidfXml) {
        try {
            log.debug("开始解析 PIDF XML: {}", pidfXml);

            // 创建 XML 解析器
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            // 解析 XML
            Document document = builder.parse(new ByteArrayInputStream(pidfXml.getBytes("UTF-8")));
            document.getDocumentElement().normalize();

            // 提取 entity 属性
            Element presenceElement = document.getDocumentElement();
            String entity = presenceElement.getAttribute("entity");

            // 提取 basic 状态
            String basicStatus = extractElementValue(document, "basic", "closed");

            // 提取 note 状态
            String note = extractElementValue(document, "note", "offline");

            PresenceInfo presenceInfo = new PresenceInfo(entity, basicStatus, note);
            log.info("PIDF 解析成功: {}", presenceInfo);

            return presenceInfo;

        } catch (Exception e) {
            log.error("解析 PIDF XML 失败", e);
            // 返回默认离线状态
            return new PresenceInfo("unknown", "closed", "offline");
        }
    }

    /**
     * 从 XML 文档中提取指定标签的值
     *
     * @param document XML 文档
     * @param tagName 标签名
     * @param defaultValue 默认值
     * @return 标签值
     */
    private String extractElementValue(Document document, String tagName, String defaultValue) {
        try {
            NodeList nodeList = document.getElementsByTagName(tagName);
            if (nodeList.getLength() > 0) {
                Element element = (Element) nodeList.item(0);
                String value = element.getTextContent();
                return value != null ? value.trim() : defaultValue;
            }
        } catch (Exception e) {
            log.warn("提取 {} 标签失败", tagName, e);
        }
        return defaultValue;
    }

    /**
     * 从 PIDF XML 中提取用户名
     *
     * @param pidfXml PIDF XML 字符串
     * @return 用户名 (从 entity 中提取)
     */
    public String extractUsername(String pidfXml) {
        PresenceInfo info = processPidf(pidfXml);
        String entity = info.getEntity();

        if (entity != null && entity.contains("sip:")) {
            // 提取 sip:username@domain 中的 username
            String sipUri = entity.replace("sip:", "");
            if (sipUri.contains("@")) {
                return sipUri.substring(0, sipUri.indexOf("@"));
            }
            return sipUri;
        }

        return "unknown";
    }

    /**
     * 从 PIDF XML 中快速提取状态字符串
     *
     * @param pidfXml PIDF XML 字符串
     * @return 状态字符串: online/busy/away/offline
     */
    public String extractStatus(String pidfXml) {
        PresenceInfo info = processPidf(pidfXml);
        return info.getNote();
    }

    /**
     * 检查用户是否在线
     *
     * @param pidfXml PIDF XML 字符串
     * @return true=在线, false=离线
     */
    public boolean isOnline(String pidfXml) {
        PresenceInfo info = processPidf(pidfXml);
        return "open".equalsIgnoreCase(info.getBasicStatus());
    }

    /**
     * 将简单状态字符串转换为整数
     *
     * @param statusString 状态字符串: online/busy/away/offline
     * @return 状态码: 0离线 1在线 2忙碌 3离开
     */
    public int statusToInt(String statusString) {
        PresenceStatus status = PresenceStatus.fromCode(statusString);
        switch (status) {
            case ONLINE:
                return 1;
            case BUSY:
                return 2;
            case AWAY:
                return 3;
            case OFFLINE:
            default:
                return 0;
        }
    }

    /**
     * 将整数状态码转换为字符串
     *
     * @param statusInt 状态码: 0离线 1在线 2忙碌 3离开
     * @return 状态字符串
     */
    public String intToStatus(int statusInt) {
        switch (statusInt) {
            case 1:
                return PresenceStatus.ONLINE.getCode();
            case 2:
                return PresenceStatus.BUSY.getCode();
            case 3:
                return PresenceStatus.AWAY.getCode();
            case 0:
            default:
                return PresenceStatus.OFFLINE.getCode();
        }
    }

    // ========== 单元测试方法 ==========

    /**
     * 测试 PIDF 解析功能
     */
    public static void main(String[] args) {
        SubscribeHandler handler = new SubscribeHandler();

        // 测试在线状态
        String onlinePidf = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<presence xmlns=\"urn:ietf:params:xml:ns:pidf\" \n" +
                "          entity=\"sip:alice@sip.example.com\">\n" +
                "  <tuple id=\"alice\">\n" +
                "    <status>\n" +
                "      <basic>open</basic>\n" +
                "    </status>\n" +
                "    <note>online</note>\n" +
                "  </tuple>\n" +
                "</presence>";

        System.out.println("=== 测试在线状态 ===");
        PresenceInfo info1 = handler.processPidf(onlinePidf);
        System.out.println(info1);
        System.out.println("是否在线: " + handler.isOnline(onlinePidf));
        System.out.println();

        // 测试忙碌状态
        String busyPidf = onlinePidf.replace("<note>online</note>", "<note>busy</note>");
        System.out.println("=== 测试忙碌状态 ===");
        PresenceInfo info2 = handler.processPidf(busyPidf);
        System.out.println(info2);
        System.out.println();

        // 测试离线状态
        String offlinePidf = onlinePidf
                .replace("<basic>open</basic>", "<basic>closed</basic>")
                .replace("<note>online</note>", "<note>offline</note>");
        System.out.println("=== 测试离线状态 ===");
        PresenceInfo info3 = handler.processPidf(offlinePidf);
        System.out.println(info3);
        System.out.println("是否在线: " + handler.isOnline(offlinePidf));
    }
}