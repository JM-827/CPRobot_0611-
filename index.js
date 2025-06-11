// functions/index.js
const { onRequest } = require("firebase-functions/v2/https");
const { setGlobalOptions } = require("firebase-functions/v2");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

admin.initializeApp();
setGlobalOptions({ region: "asia-northeast3" });

exports.textAway = onRequest(async (req, res) => {
  const text = req.body.text;
  if (!text) {
    logger.error("텍스트가 제공되지 않음");
    res.status(400).send("텍스트가 필요합니다");
    return;
  }

  try {
    await admin.firestore().collection("Messages").add({
      text: text,
      sender: "user",
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
    });
    logger.log("Firestore에 텍스트 저장:", text);
    res.status(200).send("텍스트 수신 완료");
  } catch (error) {
    logger.error("텍스트 저장 오류:", error);
    res.status(500).send("텍스트 저장 실패");
  }
});

exports.textPush = onRequest(async (req, res) => {
  const text = req.body.text;
  if (!text) {
    logger.error("텍스트가 제공되지 않음");
    res.status(400).send("텍스트가 필요합니다");
    return;
  }

  try {
    await admin.firestore().collection("Messages").add({
      text: `로봇 응답: ${text}`,
      sender: "robot",
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
    });
    logger.log("로봇 응답 전송:", text);
    res.status(200).send("응답 전송 완료");
  } catch (error) {
    logger.error("로봇 응답 전송 오류:", error);
    res.status(500).send("응답 전송 실패");
  }
});
