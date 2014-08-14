package uk.co.thinkofdeath.micromc.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

public class CipherCodec extends ByteToMessageCodec<ByteBuf> {

    private Cipher cipherEncrypt;
    private Cipher cipherDecrypt;

    private byte[] encryptBuffer = new byte[8192];
    private byte[] dataBuffer = new byte[8192];
    private byte[] deDataBuffer = new byte[8192];

    public CipherCodec(SecretKey secretKey) {
        try {
            cipherEncrypt = Cipher.getInstance("AES/CFB8/NoPadding");
            cipherEncrypt.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(secretKey.getEncoded()));

            cipherDecrypt = Cipher.getInstance("AES/CFB8/NoPadding");
            cipherDecrypt.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(secretKey.getEncoded()));
        } catch (NoSuchAlgorithmException
                | NoSuchPaddingException
                | InvalidAlgorithmParameterException
                | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        byte[] data;
        int offset = 0;
        int dataSize;
        if (!msg.isDirect()) {
            data = msg.array();
            offset = msg.arrayOffset();
            dataSize = msg.readableBytes();
            msg.skipBytes(msg.readableBytes());
        } else {
            dataSize = msg.readableBytes();
            if (dataBuffer.length < dataSize) {
                dataBuffer = new byte[dataSize];
            }
            msg.readBytes(dataBuffer, 0, dataSize);
            data = dataBuffer;
        }
        int size = cipherEncrypt.getOutputSize(msg.readableBytes());
        if (encryptBuffer.length < size) {
            encryptBuffer = new byte[size];
        }
        int count = cipherEncrypt.update(data, offset, dataSize, encryptBuffer);
        out.writeBytes(encryptBuffer, 0, count);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byte[] data;
        int offset = 0;
        int dataSize = in.readableBytes();
        if (!in.isDirect()) {
            data = in.array();
            offset = in.arrayOffset();
            in.skipBytes(in.readableBytes());
        } else {
            if (deDataBuffer.length < dataSize) {
                deDataBuffer = new byte[dataSize];
            }
            in.readBytes(deDataBuffer, 0, dataSize);
            data = deDataBuffer;
        }

        int size = cipherDecrypt.getOutputSize(dataSize);
        ByteBuf buf = ctx.alloc().heapBuffer(size);
        buf.writerIndex(cipherDecrypt.update(data, offset, dataSize, buf.array(), buf.arrayOffset()));
        out.add(buf);
    }
}
