package com.example;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 一括アップロードのコントローラー
 */
@RestController
@RequestMapping("file/upload")
public class FileUploadRestController {
	
	private static final Logger logger = LoggerFactory.getLogger(FileUploadRestController.class);
	
    /**
     * 一括アップロードを行う
     *
     * @param multipartFile
     * @param fileType
     * @param registrationType
     * @param dailyAttendanceYyyymm
     * @return
     */
    @RequestMapping(method = RequestMethod.POST)
    public Object post(
            @RequestParam("upload_file") MultipartFile multipartFile,
            @RequestParam("filetype") String fileType) {

        logger.info("fileType1 : " + fileType);
		try {
			logger.info("fileType2 : " + URLDecoder.decode(fileType, "UTF-8"));
		} catch (UnsupportedEncodingException e1) {
			// TODO 自動生成された catch ブロック
			e1.printStackTrace();
		}
			
        
        // ファイルが空の場合は異常終了
        if(multipartFile.isEmpty()){
            // 異常終了時の処理
        }

        // 保存先ディレクトリの外に書き込まれないよう、ファイル名だけを取り出す
        String fileName = safeFileName(fileType);

        if (fileName == null) {
            logger.warn("ファイル名として使用できない filetype が指定されました");
            return "error!";
        }

        // ファイル種類から決まる値をセットする
        StringBuffer filePath = new StringBuffer("/uploadfile");   //ファイルパス

        // アップロードファイルを格納するディレクトリを作成する
        File uploadDir = mkdirs(filePath);

        try {
            // アップロードファイルを置く
            File uploadFile = new File(uploadDir, fileName);
            byte[] bytes = multipartFile.getBytes();
            BufferedOutputStream uploadFileStream =
                    new BufferedOutputStream(new FileOutputStream(uploadFile));
            uploadFileStream.write(bytes);
            uploadFileStream.close();

            return "You successfully uploaded.";
        } catch (Exception e) {
        	return "error!";
        } catch (Throwable t) {
        	return "error!";
        }
    }

    /**
     * アップロードファイル名として安全な名前を取り出す
     *
     * パス区切りや ".." を含む入力からは最後の名前だけを取り出し、保存先ディレクトリの
     * 外へ書き込まれることを防ぐ。ファイル名として使えない場合は null を返す。
     *
     * @param fileType リクエストで指定されたファイル名
     * @return ディレクトリ要素を含まないファイル名。使用できない場合は null
     */
    static String safeFileName(String fileType) {

        if (fileType == null) {
            return null;
        }

        try {
            Path fileName = Paths.get(fileType).getFileName();

            if (fileName == null) {
                return null;
            }

            String name = fileName.toString();

            if (name.isBlank() || name.equals(".") || name.equals("..")) {
                return null;
            }

            return name;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * アップロードファイルを格納するディレクトリを作成する
     *
     * @param filePath
     * @return
     */
    private File mkdirs(StringBuffer filePath){
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        File uploadDir = new File(filePath.toString(), sdf.format(now));
        // 既に存在する場合はプレフィックスをつける
        int prefix = 0;
        while(uploadDir.exists()){
            prefix++;
            uploadDir =
                    new File(filePath.toString() + sdf.format(now) + "-" + String.valueOf(prefix));
            
        }

        // フォルダ作成
        uploadDir.mkdirs();

        return uploadDir;
    }
}