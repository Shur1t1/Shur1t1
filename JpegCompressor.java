package moe._47saikyo;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

public class JpegCompressor {
    private JpegIOStream            IO;                        
    private BufferedImage           image;                     
    private DCT                     dct;                       
    private EntropyEncoder          entropy;                   
    private int                     imageHeight;                
    private int                     imageWidth;                
    private int                     completionWidth;            
    private int                     completionHeight;          
    private final int               MCULength=16;              
    private final int               blockLength=MCULength/2;   

    public  ColorComponentHandler   RgbToYccHandler;
    public  ColorComponentHandler   YccToRgbHandler;

    /**
     * / * *
     * I named it the Channel Converter, which is used to convert an array of color channels in float format to another format
     */
    interface ColorComponentHandler {
        float[] get(float[] components);
    }

    /**
     *@param input Gets the inputStream of the encoder
     *@param output Gets the outputStream of the encoder
     */
    public JpegCompressor(InputStream input,OutputStream output){
        IO=new JpegIOStream(input,output);
        initJpegCompressor();
    }
    public JpegCompressor(JpegIOStream io) {
        IO=io;
        initJpegCompressor();
    }

    /**
    * Image initialization, including: <br/>
    *1. Read the picture <br/>
    *2. Obtain the height and width of the picture, and calculate the complete size <br/>
    *3. Initialize the DCT processing class and the entropy coding processing class <br/>
    *4. Define the conversion formula from RGB to YCbCr according to the formula <br/>
     */
    private void initJpegCompressor(){
        image = IO.getImage();
        imageWidth = IO.imageWidth;
        imageHeight = IO.imageHeight;
        dct=new DCT();
        entropy=new EntropyEncoder(IO.getOutput());
        // For the image size does not meet the multiple of 16, complete to the multiple of 16, convenient for later partitioning
        completionWidth = ((imageWidth % MCULength != 0) ? (int) (Math.floor((double) imageWidth / MCULength) + 1) * MCULength : imageWidth);
        completionHeight = ((imageHeight % MCULength != 0) ? (int) (Math.floor((double) imageHeight / MCULength) + 1) * MCULength : imageHeight);

        //Define the RGB 2 YCC channel converter
        RgbToYccHandler = (RGB) -> new float[]{
                (float) ((0.299 * RGB[0] + 0.587 * RGB[1] + 0.114 * RGB[2])),
                (float) ((-0.16874 * RGB[0] - 0.33126 * RGB[1] + 0.5 * RGB[2])) + 128,
                (float) ((0.5 * RGB[0] - 0.41869 * RGB[1] - 0.08131 * RGB[2])) + 128
        };

        //Define the YCC 2 RGB channel converter
        YccToRgbHandler = (YUV) -> new float[]{
                (float) (YUV[0] + 1.402 * (YUV[2] - 128)),
                (float) (YUV[0] - 0.34414 * (YUV[1] - 128) - 0.71414 * (YUV[2] - 128)),
                (float) (YUV[0] + 1.772 * (YUV[1] - 128))
        };
    }

    /**
     *Full step calls for compression, including: <br/>
     *1. Write file header <br/>
     *2. Compress file and write data stream <br/>
     *3. Write the end of the file <br/>
     */
    public void doCompress() throws IOException {
        IO.writeHeader();
        WriteCompressedData();
        IO.writeEOI();
    }
    public void setComment(String comment){
        IO.setComment(comment);
    }

    /**
     * Modify the outputStream of the encoder for cases where null is passed in the constructor's output parameter during initialization, pending compression
     * @param output encoder modified outputStream
     */
    public void setOutput(OutputStream output){
        IO.setOutput(output);
        entropy.setOutput(IO.getOutput());
    }

    /**
     * Test output. This method outputs the current state of the image in BMP format for debugging the compression process before entropy coding
     * @param File BMP image output path
     */
    public void deBugWrite(String File){
        IO.debugWrite(File);
    }

//  void WriteCompressedData(BufferedOutputStream outStream) throws IOException {

    /**
     * * Compression process, including: <br/>
     * 1. Get MCU number (x and y number) <br/>
     * 2. Obtain the specific offset value of MCU according to x and y serial number <br/>
     * 3. Color space conversion and chroma sampling of 16x16 MCU by {@link #getMCUBlock(int, int)} <br/>
     * 4. DCT and quantize the converted data matrix by {@link #DCTComp(float[][], DCT.component)} <br/>
     * 5. Entropy encoding of quantization matrix by {@link #entropyComp(int[][], EntropyEncoder.component)}, including Zigzag scanning, run length encoding, Huffman encoding, VLI encoding <br/>
     * 6. Write entropy encoded data to the file output stream <br/>
     * @throws IOException Indicates an I/O exception
     */
    private void WriteCompressedData() throws IOException {
        float[][][] Array;

        int[][][] DCTYArray = new int[4][][];
        int[][] DCTUArray;
        int[][] DCTVArray;

        int i,x,y;
        for(y=0;y*MCULength+MCULength<=completionHeight;y++){
            for (x=0;x*MCULength+MCULength<=completionWidth;x++){
                Array=getMCUBlock(x,y);

                for (i=0;i<4;i++){
                    DCTYArray[i]=DCTComp_L(Array[i]);
                }
                DCTUArray=DCTComp_C(Array[4]);
                DCTVArray=DCTComp_C(Array[5]);

                for (i=0;i<4;i++){
                    entropyComp(DCTYArray[i], EntropyEncoder.component.Y);
                }
                entropyComp(DCTUArray, EntropyEncoder.component.Cb);
                entropyComp(DCTVArray, EntropyEncoder.component.Cr);

            }
        }
        entropy.flushByte();
    }

    /**
     * * MCU blocks, obtains the 16x16 matrix corresponding to x and y serial number positions, and carries out color space conversion and chroma sampling, returning the processed 6 groups of matrices. The chroma matrix here has been processed by chroma sampling, so it is still 8x8 matrix
     * @param x Serial number of the MCU
     * @param y y serial number of the MCU
     * @return 6 sets of 8x8 float matrices, namely, upper left gray scale (Y1), upper right gray scale (Y2), lower left gray scale (Y3), lower right gray scale (Y4), chroma B(Cb), Chroma R matrix (Cr)
     */
    private float[][][] getMCUBlock(int x,int y){
        float[] RGB;
        float[] YCC;
        float[][][] Array=new float[6][blockLength][blockLength];
        for (i=0;i<4;i++){
            xOffset=x*MCULength;
            yOffset=y*MCULength;
            MCU_r_offset=0;
            MCU_c_offset=0;
            if(i==1||i==3){
                xOffset+=blockLength;
                MCU_c_offset+=blockLength;
            }
            if(i==2||i==3){
                yOffset+=blockLength;
                MCU_r_offset+=blockLength;
            }
            for (r=0;r<blockLength;r++){
                for (c=0;c<blockLength;c++){

                    if(xOffset+c>=imageWidth||yOffset+r>=imageHeight){
                        RGB=getRGB(xOffset+c>=imageWidth?imageWidth-1:xOffset+c,yOffset+r>=imageHeight?imageHeight-1:yOffset+r);
                    }else{
                        RGB=getRGB(xOffset+c,yOffset+r);
                    }

                    YCC=RgbToYccHandler.get(RGB);
                    Array[i][r][c]=YCC[0];

                    //downSampling Chrominance sampling, even line sampling U, odd line sampling V, the first step of compression, a total of 12 groups of data every 4 pixels into 6 groups
                    if((r+MCU_r_offset)%2==0&&(c+MCU_c_offset)%2==0){
                        Array[4][(r+MCU_r_offset)/2][(c+MCU_c_offset)/2]=YCC[1];
                    }
                    else if((r+MCU_r_offset)%2==1&&(c+MCU_c_offset)%2==0){
                        Array[5][(r+MCU_r_offset)/2][(c+MCU_c_offset)/2]=YCC[2];
                    }
                }
            }
        }
        return Array;
    }

    /**
     * Call DCT processing and quantization
     * @param matrix A set of 8x8 blocks for YUV color channel data
     * @param component Color channel to which the matrix belongs, luminance and chrominance
     * @return Returns the quantized matrix
     */
    private int[][] DCTComp(float[][] matrix,DCT.component component){
        dct.initMatrix(matrix);
        dct.forwardDCT();
        return dct.quantize(component);
    }

    /**
     *By inverting the quantization matrix, YCbCr matrix is obtained again for debugging output
     * @param matrix A set of 8x8 blocks, a processed quantized matrix
     * @param component Color channel to which the matrix belongs, luminance and chrominance
     * @return Returns the YUV matrix by inverse transformation
     */
    private float[][] IDCTComp(int[][] matrix,DCT.component component){
        dct.initMatrix(matrix);
        dct.reverseQuantize(component);
        return dct.reverseDCT();
    }

    /**
     ** Perform a DCT transformation on a grayscale matrix
* @param matrix 8x8 gray scale matrix
* @return The processed quantization matrix
     */
    private int[][] DCTComp_L(float[][] matrix){return DCTComp(matrix,DCT.component.luminance);}

    /**
     *DCT transformation is performed on a chrominance matrix
* @param matrix 8x8 chromaticity matrix
* @return The processed quantization matrix
     */
    private int[][] DCTComp_C(float[][] matrix){return DCTComp(matrix,DCT.component.chrominance);}

    /**
     * Inverse transformation of a quantized gray matrix
* @param matrix 8x8 grayscale quantization matrix
* @return gray scale matrix after inverse transformation
     */
    private float[][] IDCT_L(int[][] matrix){return IDCTComp(matrix,DCT.component.luminance);}

    /**
     * Invert a quantized chrominance matrix
* @param matrix 8x8 colorimetric matrix
* @return The chromaticity matrix after the inverse transformation
     */
    private float[][] IDCT_C(int[][] matrix){return IDCTComp(matrix,DCT.component.chrominance);}

    /**
     * The entropy encoding is called and written directly to the output stream
* @param matrix A set of 8x8 quantized matrices
* @param component Color channel to which the matrix belongs, luminance and chrominance
* @throws IOException Indicates an I/O exception
     */
    private void entropyComp(int[][] matrix, EntropyEncoder.component component) throws IOException {
        entropy.initMatrix(matrix);
        entropy.writeHuffmanBits(component);
    }

    /**
     * The RGB value of 3 bytes is obtained by getRGB of {@link ImageIO}, and the value of the three color channels R, G, and B is obtained by bit operation
* @param x x coordinates of the pixel
* @param y y coordinate of the pixel
* @return A float array of size 3, storing the data of R, G, and B respectively
     */
    private float[] getRGB(int x, int y) {
        if (x >= imageWidth) {
            x -= imageWidth;
            y++;
        }
        if (y >= imageHeight) {
            return null;
        }
        int value = image.getRGB(x, y);
        int r = ((value >> 16) & 0xff);
        int g = ((value >> 8) & 0xff);
        int b = (value & 0xff);
        return new float[]{r, g, b};
    }

    private void setRGB(int x, int y,float[] RGBComponent){
        //YCC may exceed [0,255] when converted back to RGB, need to judge, blood lesson (
        for (int i=0;i<3;i++){
            if(RGBComponent[i]<0)RGBComponent[i]=0;
            if(RGBComponent[i]>255)RGBComponent[i]=255;
        }
        int rgb=((int) RGBComponent[0] << 16) | ((int) RGBComponent[1] << 8) | (int) (RGBComponent[2]);
        image.setRGB(x, y, rgb);
    }
}

/**
 * JPEG file header marker bit table
 */
class JPEGHeader {
    public static final byte    marker  = (byte) 0xFF;
    public static final byte    SOI     = (byte) 0xD8;
    public static final byte    SOF0    = (byte) 0xC0;
    public static final byte    SOF2    = (byte) 0xC2;
    public static final byte    DHT     = (byte) 0xC4;
    public static final byte    DQT     = (byte) 0xDB;
    public static final byte    DRI     = (byte) 0xDD;
    public static final byte    SOS     = (byte) 0xDA;
    public static final byte[]  RSTn    ={(byte) 0xD1, (byte) 0xD2, (byte) 0xD3, (byte) 0xD4, (byte) 0xD5, (byte) 0xD6, (byte) 0xD7};
    public static final byte    COM     = (byte) 0xFE;
    public static final byte    EOI     = (byte) 0xD9;
}

class JpegIOStream {
    private BufferedImage bufferedImage;
    private BufferedOutputStream bufferedOutput;
    public int imageHeight;
    public int imageWidth;
    private byte[] comment;
    private final String defaultComment="JPEG Compressor Copyright 2023 Smile_slime_47";


    public JpegIOStream(File input, OutputStream output) {
        try {
            bufferedImage = ImageIO.read(input);
        } catch (IOException ignored) {
        }
        this.imageWidth = bufferedImage.getWidth();
        this.imageHeight = bufferedImage.getHeight();
        comment=defaultComment.getBytes();
        bufferedOutput=output!=null?new BufferedOutputStream(output):null;
    }
    /**
     * Initialize the JPEG IO class
* @param input Input stream of IO
* @param output Indicates the output stream of IO
     */
    public JpegIOStream(InputStream input, OutputStream output) {
        try {
            bufferedImage = ImageIO.read(input);
        } catch (IOException ignored) {
        }
        this.imageWidth = bufferedImage.getWidth();
        this.imageHeight = bufferedImage.getHeight();
        comment=defaultComment.getBytes();
        bufferedOutput=output!=null?new BufferedOutputStream(output):null;
    }

    public void setOutput(OutputStream output){
        bufferedOutput=new BufferedOutputStream(output);
    }

    public BufferedOutputStream getOutput(){return bufferedOutput;}

    public BufferedImage getImage() {return bufferedImage;}

    /**
     * The BMP file in the corresponding path is displayed
* @param File Output picture path
     */
    public void debugWrite(String File) {
        try {
            ImageIO.write(bufferedImage, "bmp", new File(File));
        } catch (IOException ignored) {
        }
    }

    public void setComment(String com){
        comment=com.getBytes();
    }

    private void writeMarker(byte marker) throws IOException {
        bufferedOutput.write(JPEGHeader.marker);
        bufferedOutput.write(marker);
    }
    private void writeByte(byte data) throws IOException {
        bufferedOutput.write(data);
    }

    private void writeArray(byte[] dataArr) throws IOException {
        bufferedOutput.write(dataArr);
    }

    public void writeEOI() throws IOException {
        writeMarker(JPEGHeader.EOI);
        bufferedOutput.flush();
    }


    public void writeHeader() throws IOException {
        int[] zigzagDQT;
        int[] bitsDHT,valDHT;
        writeMarker(JPEGHeader.SOI);

        writeMarker((byte) 0xE0);
        byte[] JFIFPayload={
                0x00,
                0x10,
                0x4A,
                0x46,
                0x49,
                0x46,
                0x00,
                0x01,
                0x01,
                0x00,
                0x00,
                0x01,
                0x00,
                0x01,
                0x00,
                0x000
        };
        writeArray(JFIFPayload);

        writeMarker(JPEGHeader.COM);
        writeByte((byte) ((comment.length>>8)&0xFF));
        writeByte((byte) ((comment.length)&0xFF));
        writeArray(comment);

        writeMarker(JPEGHeader.DQT);
        zigzagDQT=EntropyEncoder.zigzagScan(DCT.quantum_luminance);
        writeByte((byte) 0x00);
        writeByte((byte) 0x43);
        writeByte((byte) 0x00);
        for (int i:zigzagDQT){
            writeByte((byte) i);
        }

        writeMarker(JPEGHeader.DQT);
        zigzagDQT=EntropyEncoder.zigzagScan(DCT.quantum_chrominance);
        writeByte((byte) 0x00);
        writeByte((byte) 0x43);
        writeByte((byte) 0x01);
        for (int i:zigzagDQT){
            writeByte((byte) i);
        }

        writeMarker(JPEGHeader.SOF0);
        byte[] SOF0Payload={
                0x00,
                0x11,
                0x08,
                (byte) ((imageHeight>>8)&0xFF),
                (byte) ((imageHeight)&0xFF),
                (byte) ((imageWidth>>8)&0xFF),
                (byte) ((imageWidth)&0xFF),
                0x03,
                0x01,
                (2<<4)+2,
                0x00,
                0x02,
                (1<<4)+1,
                0x01,
                0x03,
                (1<<4)+1,
                0x01,
        };
        writeArray(SOF0Payload);

        //Dht_huffman table segment
        writeMarker(JPEGHeader.DHT);
        bitsDHT=EntropyEncoder.bitsDCluminance;
        valDHT=EntropyEncoder.valDCluminance;
        writeByte((byte) (((2+bitsDHT.length+valDHT.length)>>8)&0xFF));
        writeByte((byte) ((2+bitsDHT.length+valDHT.length)&0xFF));
        for (int i:bitsDHT){
            writeByte((byte) i);
        }
 
        //Start Of Scan
        writeMarker(JPEGHeader.SOS);
        byte[] SOSPayload={
                0x00,
                0x0C,
                0x03,
                0x01,
                (0<<4)+0,
                0x02,
                (1<<4)+1,
                0x03,
                (1<<4)+1,
                0x00,
                0x3F,
                0x00,
        };
        writeArray(SOSPayload);
    }
}

class DCT{
    private float[][]       matrix;                                            
    private float[][]       DCTMatrix;                                          
    private int[][]         quantumMatrix;                                     
    private final int       blockLength=8;                                      
    private final double    DCConstant_uv=((double)1/Math.sqrt(blockLength));   
    private final double    ACConstant_uv=(Math.sqrt(2)/Math.sqrt(blockLength));
    public  enum            component{luminance, chrominance};                 

    //Grayscale quantization table provided by JPEG standard
    public static final int[][]   quantum_luminance={
            {16,11,10,16,24,40,51,61},
            {12,12,14,19,26,58,60,55},
            {14,13,16,24,40,57,69,56},
            {14,17,22,29,51,87,80,62},
            {18,22,37,56,68,109,103,77},
            {24,35,55,64,81,104,113,92},
            {49,64,78,87,103,121,120,101},
            {72,92,95,98,112,100,103,99}
    };
    //Color quantization table provided by JPEG standard
    public static final int[][]   quantum_chrominance={
            {17,18,24,47,99,99,99,99},
            {18,21,26,66,99,99,99,99},
            {24,26,56,99,99,99,99,99},
            {47,66,99,99,99,99,99,99},
            {99,99,99,99,99,99,99,99},
            {99,99,99,99,99,99,99,99},
            {99,99,99,99,99,99,99,99},
            {99,99,99,99,99,99,99,99}
    };

    /**
     * Initializes the matrix, treats the float matrix as a YUV matrix when passed in and prepares the positive transformation
     */
    public void initMatrix(float[][] matrix){
        if(matrix.length==blockLength){
            this.matrix=new float[blockLength][blockLength];
            DCTMatrix=new float[blockLength][blockLength];
            quantumMatrix=new int[blockLength][blockLength];
            for (int r=0;r<blockLength;r++){
                for (int c=0;c<blockLength;c++){
                    this.matrix[r][c]=matrix[r][c];
                    DCTMatrix[r][c]=matrix[r][c];
                }
            }
            symmetry();
        }
    }

    /**
     * Initializes the matrix, treats it as a quantized matrix when an int matrix is passed in and prepares the inverse transformation
     */
    public void initMatrix(int[][] matrix){
        if(matrix.length==blockLength){
            this.matrix=new float[blockLength][blockLength];
            DCTMatrix=new float[blockLength][blockLength];
            quantumMatrix=new int[blockLength][blockLength];
            //手动深拷贝.jpg
            for (int r=0;r<blockLength;r++){
                for (int c=0;c<blockLength;c++){
                    quantumMatrix[r][c]= matrix[r][c];
                }
            }
        }
    }

    /**
     * The Y matrix data obtained after channel conversion ranges from 0 to 255, and DCT needs to define domain symmetry and shift the data 128 to the left so that it falls in the range of -128 to 127
     */
    private void symmetry(){
        for(int r=0;r<blockLength;r++){
            for(int c=0;c<blockLength;c++){
                matrix[r][c]-=128;
            }
        }
    }

    private interface cosCalculator {
        double get(int output,int input);
    }

    /**
     * The YUV matrix is converted into a DCT frequency domain matrix by positive transformation of DCT
* @return DCT frequency domain matrix
     */
    float[][] forwardDCT(){
        DCTMatrix=new float[blockLength][blockLength];
        double constant_2;
        double constant_3;

        cosCalculator cos=(u, x)->{
            double constant_top=u*Math.PI*((2*x)+1);
            double constant_bottom=2*blockLength;

            return Math.cos(constant_top/constant_bottom);
        };

        for(int v=0;v<blockLength;v++){
            for(int u=0;u<blockLength;u++){
                constant_2=(u==0?DCConstant_uv:ACConstant_uv)*(v==0?DCConstant_uv:ACConstant_uv);
                constant_3=0;
                for (int y=0;y<blockLength;y++){
                    for (int x=0;x<blockLength;x++){
                        constant_3+=matrix[y][x]* cos.get(u,x)*cos.get(v,y);
                    }
                }
                DCTMatrix[v][u]= (float) (constant_2*constant_3);
            }
        }
        return DCTMatrix;
    }
    int[][] quantize(component channel_type){
        quantumMatrix=new int[blockLength][blockLength];
        for (int v=0;v<blockLength;v++){
            for(int u=0;u<blockLength;u++){
                if(channel_type==component.luminance){
                    quantumMatrix[v][u]=Math.round(DCTMatrix[v][u]/quantum_luminance[v][u]);
                }else if(channel_type==component.chrominance){
                    quantumMatrix[v][u]=Math.round(DCTMatrix[v][u]/quantum_chrominance[v][u]);
                }
            }
        }
        return quantumMatrix;
    }

     */
    float[][] reverseDCT(){
        matrix=new float[blockLength][blockLength];
        //double constant_1=(double)2/blockLength;
        double constant;

        cosCalculator cos=(u, x)->{
            double constant_top=u*Math.PI*((2*x)+1);
            double constant_bottom=2*blockLength;

            return Math.cos(constant_top/constant_bottom);
        };

        for(int y=0;y<blockLength;y++){
            for(int x=0;x<blockLength;x++){
                for (int v=0;v<blockLength;v++){
                    for (int u=0;u<blockLength;u++){
                        constant=(u==0?DCConstant_uv:ACConstant_uv)*(v==0?DCConstant_uv:ACConstant_uv);
                        matrix[y][x] += (float) (constant*DCTMatrix[v][u]* cos.get(u,x)*cos.get(v,y));
                    }
                }
            }
        }

        for(int r=0;r<blockLength;r++){
            for(int c=0;c<blockLength;c++){
                matrix[r][c]+=128;
            }
        }

        return matrix;
    }

    float[][] reverseQuantize(component channel_type){
        DCTMatrix=new float[blockLength][blockLength];
        for (int v=0;v<blockLength;v++){
            for(int u=0;u<blockLength;u++){
                if(channel_type==component.luminance){
                    DCTMatrix[v][u]=quantumMatrix[v][u]*quantum_luminance[v][u];
                }else if(channel_type==component.chrominance){
                    DCTMatrix[v][u]=quantumMatrix[v][u]*quantum_chrominance[v][u];
                }
            }
        }
        return DCTMatrix;
    }
}


/**
 * Entropy coding class
 */
class EntropyEncoder{
    private BufferedOutputStream output;      
    private int[] lastDC={0,0,0};             
    private static final int blockLength=8;     
    private int[] zigzagArray;                
    private byte bufByte=0;                    
    private int  bufIndex=7;                    
    public enum component{Y, Cb,Cr};
    private static final int[][] zigzagOrder={{0, 0},{0, 1},{1, 0},{2, 0},{1, 1},{0, 2},{0, 3},{1, 2},{2, 1},{3, 0},{4, 0},{3, 1},{2, 2},{1, 3},{0, 4},{0, 5},{1, 4},{2, 3},{3, 2},{4, 1},{5, 0},{6, 0},{5, 1},{4, 2},{3, 3},{2, 4},{1, 5},{0, 6},{0, 7},{1, 6},{2, 5},{3, 4},{4, 3},{5, 2},{6, 1},{7, 0},{7, 1},{6, 2},{5, 3},{4, 4},{3, 5},{2, 6},{1, 7},{2, 7},{3, 6},{4, 5},{5, 4},{6, 3},{7, 2},{7, 3},{6, 4},{5, 5},{4, 6},{3, 7},{4, 7},{5, 6},{6, 5},{7, 4},{7, 5},{6, 6},{5, 7},{6, 7},{7, 6},{7, 7}};
    //private final int[][]   zigzagOrder_luminance={{0, 0},{0, 1},{1, 0},{2, 0},{1, 1},{0, 2},{0, 3},{1, 2},{2, 1},{3, 0},{4, 0},{3, 1},{2, 2},{1, 3},{0, 4},{0, 5},{1, 4},{2, 3},{3, 2},{4, 1},{5, 0},{6, 0},{5, 1},{4, 2},{3, 3},{2, 4},{1, 5},{0, 6},{0, 7},{1, 6},{2, 5},{3, 4},{4, 3},{5, 2},{6, 1},{7, 0},{7, 1},{6, 2},{5, 3},{4, 4},{3, 5},{2, 6},{1, 7},{2, 7},{3, 6},{4, 5},{5, 4},{6, 3},{7, 2},{7, 3},{6, 4},{5, 5},{4, 6},{3, 7},{4, 7},{5, 6},{6, 5},{7, 4},{7, 5},{6, 6},{5, 7},{6, 7},{7, 6},{7, 7}};
    //private final int[][]   zigzagOrder_chrominance={{0, 0},{0, 1},{1, 0},{2, 0},{1, 1},{0, 2},{0, 3},{1, 2},{2, 1},{3, 0},{3, 1},{2, 2},{1, 3},{2, 3},{3, 2},{3, 3}};

    private int[][] DCLuminanceMap;
    private int[][] DCChrominanceMap;
    private int[][] ACLuminanceMap;
    private int[][] ACChrominanceMap;

    public static final int[] bitsDCluminance = {0x00, 0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0};
    public static final int[] valDCluminance = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    public static final int[] bitsDCchrominance = {0x01, 0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0};
    public static final int[] valDCchrominance = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    public static final int[] bitsACluminance = {0x10, 0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d};
    public static final int[] valACluminance =
            {0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12,
                    0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
                    0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
                    0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0,
                    0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16,
                    0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
                    0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
                    0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
                    0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
                    0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
                    0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
                    0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
                    0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98,
                    0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7,
                    0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6,
                    0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5,
                    0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4,
                    0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
                    0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea,
                    0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
                    0xf9, 0xfa};
    public static final int[] bitsACchrominance = {0x11, 0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77};
    public static final int[] valACchrominance =
            {0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21,
                    0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
                    0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91,
                    0xa1, 0xb1, 0xc1, 0x09, 0x23, 0x33, 0x52, 0xf0,
                    0x15, 0x62, 0x72, 0xd1, 0x0a, 0x16, 0x24, 0x34,
                    0xe1, 0x25, 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
                    0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38,
                    0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
                    0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
                    0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
                    0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
                    0x79, 0x7a, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
                    0x88, 0x89, 0x8a, 0x92, 0x93, 0x94, 0x95, 0x96,
                    0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5,
                    0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4,
                    0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3,
                    0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2,
                    0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda,
                    0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9,
                    0xea, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
                    0xf9, 0xfa};

    public EntropyEncoder(BufferedOutputStream output){
        this.output=output;
        matrix=new int[blockLength][blockLength];
        zigzagArray=new int[blockLength*blockLength];
        initHuf();
    }

    public void setOutput(BufferedOutputStream output){
        this.output=output;
    }

    public void initHuf(){
        int[][] DC_matrix0 = new int[12][2];
        int[][] DC_matrix1 = new int[12][2];
        int[][] AC_matrix0 = new int[255][2];
        int[][] AC_matrix1 = new int[255][2];
//        DC_matrix = new int[2][][];
//        AC_matrix = new int[2][][];
        int p, l, i, lastp, si, code;
        int[] huffsize = new int[257];
        int[] huffcode = new int[257];

        /*
         * init of the DC values for the chrominance
         * [][0] is the code   [][1] is the number of bit
         */

        p = 0;
        for (l = 1; l <= 16; l++) {
            for (i = 1; i <= bitsDCchrominance[l]; i++) {
                huffsize[p++] = l;
            }
        }
        huffsize[p] = 0;
        lastp = p;

        code = 0;
        si = huffsize[0];
        p = 0;
        while (huffsize[p] != 0) {
            while (huffsize[p] == si) {
                huffcode[p++] = code;
                code++;
            }
            code <<= 1;
            si++;
        }

        for (p = 0; p < lastp; p++) {
            DC_matrix1[valDCchrominance[p]][0] = huffcode[p];
            DC_matrix1[valDCchrominance[p]][1] = huffsize[p];
        }

        p = 0;
        for (l = 1; l <= 16; l++) {
            for (i = 1; i <= bitsACchrominance[l]; i++) {
                huffsize[p++] = l;
            }
        }
        huffsize[p] = 0;
        lastp = p;

        code = 0;
        si = huffsize[0];
        p = 0;
        while (huffsize[p] != 0) {
            while (huffsize[p] == si) {
                huffcode[p++] = code;
                code++;
            }
            code <<= 1;
            si++;
        }

        for (p = 0; p < lastp; p++) {
            AC_matrix1[valACchrominance[p]][0] = huffcode[p];
            AC_matrix1[valACchrominance[p]][1] = huffsize[p];
        }

        p = 0;
        for (l = 1; l <= 16; l++) {
            for (i = 1; i <= bitsDCluminance[l]; i++) {
                huffsize[p++] = l;
            }
        }
        huffsize[p] = 0;
        lastp = p;

        code = 0;
        si = huffsize[0];
        p = 0;
        while (huffsize[p] != 0) {
            while (huffsize[p] == si) {
                huffcode[p++] = code;
                code++;
            }
            code <<= 1;
            si++;
        }

        for (p = 0; p < lastp; p++) {
            DC_matrix0[valDCluminance[p]][0] = huffcode[p];
            DC_matrix0[valDCluminance[p]][1] = huffsize[p];
        }

        p = 0;
        for (l = 1; l <= 16; l++) {
            for (i = 1; i <= bitsACluminance[l]; i++) {
                huffsize[p++] = l;
            }
        }
        huffsize[p] = 0;
        lastp = p;

        code = 0;
        si = huffsize[0];
        p = 0;
        while (huffsize[p] != 0) {
            while (huffsize[p] == si) {
                huffcode[p++] = code;
                code++;
            }
            code <<= 1;
            si++;
        }
        for (int q = 0; q < lastp; q++) {
            AC_matrix0[valACluminance[q]][0] = huffcode[q];
            AC_matrix0[valACluminance[q]][1] = huffsize[q];
        }

        DCLuminanceMap=DC_matrix0;
        DCChrominanceMap=DC_matrix1;
        ACLuminanceMap=AC_matrix0;
        ACChrominanceMap=AC_matrix1;
    }

    /**
     * Initializes the matrix, takes a quantized matrix and zigzag scans it into a one-dimensional matrix of 64
     */
    void initMatrix(int[][] matrix){
        this.matrix=matrix;
        zigzagArray=zigzagScan(matrix);
    }

    /**
     * zigzag scans, converting a two-dimensional array of 8x8 to a one-dimensional array of 64, and centralizing the top-left data to the front
     */
    public static int[] zigzagScan(int[][] input){
        int[] output=new int[blockLength*blockLength];
        for (int i=0;i<blockLength*blockLength;i++){
            output[i]=input[zigzagOrder[i][0]][zigzagOrder[i][1]];
        }
        return output;
    }

    interface significantWriter{
        void write(int input,int bits) throws IOException;
    }


    void writeHuffmanBits(component type) throws IOException {
        writeHuffmanBits(type,false);
    }

    void writeHuffmanBits(component type,boolean debug) throws IOException {
        significantWriter writer=(input,bits)->{
            for (int i=bits-1;i>=0;i--){
                writeByte(input&(1<<i),debug);
            }
        };
        int componentID;
        if(type==component.Y)componentID=0;
        else if(type==component.Cb)componentID=1;
        else componentID=2;

        int zeroCnt=0;
        int EOB;
        int  hufIndex;
        byte hufCode;
        int tmp=zigzagArray[0];
        zigzagArray[0]-=lastDC[componentID];
        lastDC[componentID]=tmp;
        EOB=0;
        for (int i=zigzagArray.length-1;i>=0;i--){
            if(zigzagArray[i]!=0){
                EOB=i;
                break;
            }
        }

        //Huffman编码部分
        int DCSize=VLI(zigzagArray[0],false);
        if(type==component.Y){
            writer.write(DCLuminanceMap[DCSize][0],DCLuminanceMap[DCSize][1]);
        }else{
            writer.write(DCChrominanceMap[DCSize][0],DCChrominanceMap[DCSize][1]);
        }
        //VLI编码部分
        VLI(zigzagArray[0],true,debug);

        //AC系数编码
         for (int i=1;i<zigzagArray.length;i++){
            if(zigzagArray[i]==0)zeroCnt++;
            //16个前导0的情况
            if(zeroCnt==16){
                //F/0(ZRL标记位)，标记15个前导0加上自身共16个前导0
                if(type==component.Y){
                    writer.write(ACLuminanceMap[0xF0][0],ACLuminanceMap[0xF0][1]);
                }else{
                    writer.write(ACChrominanceMap[0xF0][0],ACChrominanceMap[0xF0][1]);
                }
                zeroCnt=0;
                //这时不需要写入VLI，VLI中0也没有对应的码值
            }
            if(zigzagArray[i]!=0){
                hufCode=0;
                //高四位记录前导零数量
                for (int j=7;j>=4;j--){
                    hufCode=writeByte(hufCode,((zeroCnt&(1<<(j-4)))==0?0:1),j);
                }
                zeroCnt=0;
                //低四位记录VLI位深
                for (int j=3;j>=0;j--){
                    hufCode=writeByte(hufCode,((VLI(zigzagArray[i],false)&(1<<(j)))==0?0:1),j);
                }
                //写入hufCode
                hufIndex=Byte.toUnsignedInt(hufCode);
                if(type==component.Y){
                    writer.write(ACLuminanceMap[hufIndex][0],ACLuminanceMap[hufIndex][1]);
                }else{
                    writer.write(ACChrominanceMap[hufIndex][0],ACChrominanceMap[hufIndex][1]);
                }
                //写入VLICode
                VLI(zigzagArray[i],true,debug);
            }
            //达到EOB
            if(i>=EOB&&EOB<63){
                //写入EOB标记位然后break
                if(type==component.Y){
                    writer.write(ACLuminanceMap[0][0],ACLuminanceMap[0][1]);
                }else{
                    writer.write(ACChrominanceMap[0][0],ACChrominanceMap[0][1]);
                }
                break;
            }
        }
        //flushByte();
    }

    /**
     * 写比特
     * @param bit 0/1
     * @throws IOException IO异常
     */
    private void writeByte(int bit) throws IOException {
        writeByte(bit,false);
    }

    /**
     * 写比特，基于{@link #writeByte(byte, int, int)}实现
     * @param bit 0/1
     * @param debug 是否打印在终端中打印bit流，用于调试用途
     * @throws IOException IO异常
     */
    private void writeByte(int bit,boolean debug) throws IOException {
        if(debug){
            System.out.print(bit==0?0:1);
        }
        if(bit==0){
            bufByte=writeByte(bufByte,0,bufIndex);
            bufIndex--;
        }else{
            bufByte=writeByte(bufByte,1,bufIndex);
            bufIndex--;
        }
        if(bufIndex==-1){
            flushByte(debug);
        }
    }

    /**
     * 在input的某一位上写比特，是其他写比特方法的基础实现
     * @param input byte数据，通常传入该类的{@link #bufByte}
     * @param bit 0/1
     * @param index 0~7
     * @return 写入完毕的byte结果
     */
    private byte writeByte(byte input,int bit,int index) {
        if(bit==0){
            input= (byte) (input&((~1)<<index));
        }else{
            input=(byte) (input|(1<<index));
        }
        return input;
    }

    /**
     * flush，强行将当前的bufByte以当前状态写入输出流中
     * @throws IOException IO异常
     */
    public void flushByte() throws IOException {
        flushByte(false);
    }

    /**
     * flush，强行将当前的bufByte以当前状态写入输出流中
     * @param debug 是否打印在终端中打印bit流，用于调试用途
     * @throws IOException IO异常
     */
    public void flushByte(boolean debug) throws IOException {
        if(bufIndex<7){
            if(debug){
                System.out.println();
            }
            output.write(bufByte);
            if(bufByte==(byte)0xFF){
                //写入0xFF时再写入0x00防止误读
                output.write(0x00);
            }
            bufByte=0;
            bufIndex=7;
        }
    }


    /** VLI可变长整数编码
     * @return 返回VLI编码的位长
     * @param num 被编码数
     * @param write 是否写入output流
     */
    public int VLI(int num,boolean write) throws IOException {
        return VLI(num,write,false);
    }
    public int VLI(int num,boolean write,boolean debug) throws IOException {
        int size=0;
        if(num>0){
            for(int i=31;i>=0;i--){
                if((num&(1<<i))!=0)break;
                size++;
            }
            size=32-size;
            if(write){
                for (int i=size-1;i>=0;i--){
                    writeByte(num&(1<<i),debug);
                }
            }
        }else{
            num=-num;
            for(int i=31;i>=0;i--){
                if((num&(1<<i))!=0)break;
                size++;
            }
            size=32-size;
            if(write){
                num=~(num);
                for (int i=size-1;i>=0;i--){
                    writeByte(num&(1<<i),debug);
                }
            }
        }
        return size;
    }
}
