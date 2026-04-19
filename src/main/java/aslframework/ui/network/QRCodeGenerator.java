package aslframework.ui.network;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;

import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;

import java.util.Map;

/**
 * Generates a QR code as a JavaFX {@link WritableImage} using ZXing.
 *
 * <p>Usage:
 * <pre>{@code
 * String url = "http://192.168.1.5:8080";
 * WritableImage qr = QRCodeGenerator.generate(url, 260);
 * imageView.setImage(qr);
 * }</pre>
 */
public class QRCodeGenerator {

  private QRCodeGenerator() {}

  /**
   * Generates a square QR code image for the given content.
   *
   * @param content the URL or text to encode
   * @param size    pixel width and height of the output image
   * @return a {@link WritableImage} containing the QR code (black on white)
   */
  public static WritableImage generate(String content, int size) {
    try {
      QRCodeWriter writer = new QRCodeWriter();
      Map<EncodeHintType, Object> hints = Map.of(
          EncodeHintType.MARGIN, 1,
          EncodeHintType.CHARACTER_SET, "UTF-8"
      );
      BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE,
          size, size, hints);

      WritableImage image = new WritableImage(size, size);
      PixelWriter   pw    = image.getPixelWriter();

      for (int y = 0; y < size; y++) {
        for (int x = 0; x < size; x++) {
          // Dark theme: teal on dark background
          javafx.scene.paint.Color color = matrix.get(x, y)
              ? javafx.scene.paint.Color.web("#1ABCB8")   // module
              : javafx.scene.paint.Color.web("#0D0D0F");  // background
          pw.setColor(x, y, color);
        }
      }
      return image;

    } catch (Exception e) {
      System.err.println("[QRCodeGenerator] Failed: " + e.getMessage());
      return new WritableImage(size, size);
    }
  }
}