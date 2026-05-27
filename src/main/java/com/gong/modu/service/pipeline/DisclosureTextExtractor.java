package com.gong.modu.service.pipeline;

import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// DART 공시 원문 ZIP 파일에서 텍스트를 추출하는 클래스
@Slf4j
@Component
public class DisclosureTextExtractor {

    // 텍스트 추출 대상으로 볼 파일 확장자 목록
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".xml",
            ".html",
            ".htm",
            ".txt"
    );

    // ZIP 파일 byte[]를 받아 내부 파일들의 텍스트를 하나로 합치는 메서드
    public String extractTextFromZip(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            log.warn("[DisclosureTextExtractor] ZIP byte[] 비어있음 (length={})", zipBytes == null ? "null" : 0);
            return "";
        }

        log.info("[DisclosureTextExtractor] ZIP 추출 시작: zipSize={} bytes", zipBytes.length);

        StringBuilder result = new StringBuilder(); // 여러 파일에서 추출한 텍스트를 누적하기 위한 StringBuilder
        List<String> entryNames = new ArrayList<>();
        int textFileCount = 0;

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;

            while((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String fileName = entry.getName(); // ZIP 내부 파일명을 가져옴
                entryNames.add(fileName);

                // 텍스트 추출 대상 파일만 처리
                if (!isTextFile(fileName)) {
                    continue;
                }

                textFileCount++;

                // 현재 ZIP entry의 바이트 내용을 읽음
                byte[] fileBytes = readCurrentEntry(zipInputStream);

                // 바이트 배열을 문자열로 변환
                String rawText = decodeToString(fileBytes);

                // HTML/XML 태그를 제거하고 공백을 정리
                String plainText = stripMarkupAndNormalize(rawText);

                // 어느 파일에서 추출했는지 구분하기 위해 파일명을 함께 남김
                result.append("\n\n")
                        .append("===== FILE: ")
                        .append(fileName)
                        .append(" =====\n")
                        .append(plainText);
            }
        } catch (IOException e) { // ZIP 처리 중 IOException이 발생하면 공시 파싱 단계의 실패로 판단
            log.warn("[DisclosureTextExtractor] ZIP IOException: zipSize={}, error={}", zipBytes.length, e.getMessage());
            throw new CustomException(ErrorCode.DISCLOSURE_PARSING_FAILED);
        }

        String extracted = result.toString().trim();
        log.info(
                "[DisclosureTextExtractor] ZIP 추출 완료: entries={}, textFiles={}, extractedLength={}, sampleEntries={}",
                entryNames.size(),
                textFileCount,
                extracted.length(),
                entryNames.size() > 5 ? entryNames.subList(0, 5) : entryNames
        );

        // 누적된 텍스트 반환
        return extracted;
    }

    // 파일명이 텍스트 추출 대상 확장자인지 확인하는 메서드
    private boolean isTextFile(String fileName) {
        if (fileName == null)
            return false;

        String lowerName = fileName.toLowerCase();

        return TEXT_EXTENSIONS.stream()
                .anyMatch(lowerName::endsWith);
    }

    // 현재 Zip entry의 전체 바이트를 읽는 메서드
    private byte[] readCurrentEntry(ZipInputStream zipInputStream) throws IOException {
        // 읽은 데이터를 임시로 담을 저장소
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // 한 번에 읽을 버퍼 크기
        byte[] buffer = new byte[4096];

        // 실제로 읽은 바이트 수를 담을 변수
        int length;

        while ((length = zipInputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }

        return outputStream.toByteArray();
    }

    // byte[]를 문자열로 변환하는 메서드
    private String decodeToString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        // UTF-8로 디코딩
        String utf8Text = new String(bytes, StandardCharsets.UTF_8);

        if (!looksBroken(utf8Text)) {
            return utf8Text;
        }

        return new String(bytes, Charset.forName("MS949"));
    }

    // 문자열이 깨졌는지 판단하는 메서드
    private boolean looksBroken(String text) {
        if (text == null)
            return false;

        // 유니코드 replacement character 개수를 셈
        long brokenCount = text.chars()
                .filter(ch -> ch == '\uFFFD')
                .count();

        return brokenCount >= 10;
    }

    // HTML/XML 태그를 제거하고 공백을 정리하는 메서드
    private String stripMarkupAndNormalize(String rawText) {
        if (rawText == null)
            return "";

        return rawText
                // script 태그 내부는 화면 표시 텍스트가 아니므로 제거
                .replaceAll("(?is)<script.*?</script>", " ")
                // style 태그 내부도 화면 표시 텍스트가 아니므로 제거
                .replaceAll("(?is)<style.*?</style>", " ")
                // HTML/XML 태그를 제거
                .replaceAll("(?s)<[^>]+>", " ")
                // HTML 공백 엔티티를 일반 공백으로 바꿈
                .replace("&nbsp;", " ")
                // 자주 등장하는 HTML 엔티티를 처리
                .replace("&amp;", "&")
                // 여러 공백을 하나의 공백으로 줄임
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                // 여러 줄바꿈을 최대 두 줄 정도로 줄임
                .replaceAll("\\n{3,}", "\n\n")
                // 앞뒤 공백을 제거
                .trim();
    }
}
