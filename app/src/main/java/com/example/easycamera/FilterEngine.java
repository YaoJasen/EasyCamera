package com.example.easycamera;


import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_FRAGMENT_SHADER;
import static android.opengl.GLES20.GL_TRIANGLES;
import static android.opengl.GLES20.GL_VERTEX_SHADER;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glAttachShader;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glCompileShader;
import static android.opengl.GLES20.glCreateProgram;
import static android.opengl.GLES20.glCreateShader;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glGetAttribLocation;
import static android.opengl.GLES20.glGetError;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glLinkProgram;
import static android.opengl.GLES20.glShaderSource;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

/**
 * Created by lb6905 on 2017/6/12.
 */

public class FilterEngine {

    private static FilterEngine filterEngine = null;

    private Context mContext;
    private FloatBuffer mBuffer;
    private int mOESTextureId = -1;
    private int vertexShader = -1;
    private int fragmentShader = -1;

    private int mShaderProgram = -1;

    private int aPositionLocation = -1;
    private int aTextureCoordLocation = -1;
    private int uTextureMatrixLocation = -1;
    private int uTextureSamplerLocation = -1;

    public FilterEngine(int OESTextureId, Context context) {
        mContext = context;
        mOESTextureId = OESTextureId;
        mBuffer = createBuffer(vertexData);
        vertexShader = loadShader(GL_VERTEX_SHADER, Utils.readShaderFromResource(mContext, R.raw.base_vertex_shader));
        fragmentShader = loadShader(GL_FRAGMENT_SHADER, Utils.readShaderFromResource(mContext, R.raw.base_fragment_shader));
        //将顶点着色器、片段着色器进行链接，组装成一个OpenGL程序
        mShaderProgram = linkProgram(vertexShader, fragmentShader);
    }

    /**
     * 前两个为顶点坐标
     * 后两个为纹理坐标
     */
    private static final float[] vertexData = {
            1f, 1f, 1f, 1f,
            -1f, 1f, 0f, 1f,
            -1f, -1f, 0f, 0f,
            1f, 1f, 1f, 1f,
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f
    };

    public static final String POSITION_ATTRIBUTE = "aPosition";
    public static final String TEXTURE_COORD_ATTRIBUTE = "aTextureCoordinate";
    public static final String TEXTURE_MATRIX_UNIFORM = "uTextureMatrix";
    public static final String TEXTURE_SAMPLER_UNIFORM = "uTextureSampler";

    //将数据传递到Native层内存缓冲中
    public FloatBuffer createBuffer(float[] vertexData) {
        FloatBuffer buffer = ByteBuffer
                .allocateDirect(vertexData.length * 4)// 分配顶点坐标分量个数 * Float占的Byte位数
                .order(ByteOrder.nativeOrder())// 按照本地字节序排序
                .asFloatBuffer();// Byte类型转Float类型

        // 将Java Dalvik的内存数据复制到Native内存中
        buffer.put(vertexData, 0, vertexData.length).position(0);
        return buffer;
    }

    public int loadShader(int type, String shaderSource) {
        //创建一个新的着色器对象
        int shader = glCreateShader(type);
        if (shader == 0) {
            throw new RuntimeException("Create Shader Failed!" + glGetError());
        }
        // 将着色器代码上传到着色器对象中
        glShaderSource(shader, shaderSource);
        //编译着色器对象
        glCompileShader(shader);
        return shader;
    }

    public int linkProgram(int verShader, int fragShader) {
        // 1.创建一个OpenGL程序对象
        int program = glCreateProgram();
        // 2.获取创建状态
        if (program == 0) {
            throw new RuntimeException("Create Program Failed!" + glGetError());
        }
        // 3.将顶点着色器依附到OpenGL程序对象
        glAttachShader(program, verShader);
        // 3.将片段着色器依附到OpenGL程序对象
        glAttachShader(program, fragShader);
        // 4.将两个着色器链接到OpenGL程序对象
        glLinkProgram(program);
        //通知OpenGL开始使用该程序
        glUseProgram(program);
        return program;
    }

    public void drawTexture(float[] transformMatrix) {
        // 获取顶点坐标属性在OpenGL程序中的索引
        aPositionLocation = glGetAttribLocation(mShaderProgram, FilterEngine.POSITION_ATTRIBUTE);
        aTextureCoordLocation = glGetAttribLocation(mShaderProgram, FilterEngine.TEXTURE_COORD_ATTRIBUTE);

        // 获取颜色Uniform在OpenGL程序中的索引
        uTextureMatrixLocation = glGetUniformLocation(mShaderProgram, FilterEngine.TEXTURE_MATRIX_UNIFORM);
        uTextureSamplerLocation = glGetUniformLocation(mShaderProgram, FilterEngine.TEXTURE_SAMPLER_UNIFORM);

        glActiveTexture(GLES20.GL_TEXTURE0);
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mOESTextureId);
        //指定一个当前的textureParamHandle对象为一个全局的uniform 变量
        glUniform1i(uTextureSamplerLocation, 0);
        glUniformMatrix4fv(uTextureMatrixLocation, 1, false, transformMatrix, 0);

        if (mBuffer != null) {
            // 将缓冲区的指针移动到头部，保证数据是从最开始处读取
            mBuffer.position(0);
            //在用VertexAttribArray前必须先激活它
            glEnableVertexAttribArray(aPositionLocation);
            // 关联顶点坐标属性和缓存数据
            // 1. 位置索引；
            // 2. 每个顶点属性需要关联的分量个数(必须为1、2、3或者4。初始值为4。)；
            // 3. 数据类型；
            // 4. 指定当被访问时，固定点数据值是否应该被归一化(GL_TRUE)或者直接转换为固定点值(GL_FALSE)(只有使用整数数据时)
            // 5. 指定连续顶点属性之间的偏移量。如果为0，那么顶点属性会被理解为：它们是紧密排列在一起的。初始值为0。
            // 6. 数据缓冲区
            //指定positionHandle的数据值可以在什么地方访问。 vertexBuffer在内部（NDK）是个指针，指向数组的第一组值的内存
            glVertexAttribPointer(aPositionLocation, 2, GL_FLOAT, false, 16, mBuffer);

            mBuffer.position(2);

            // 通知GL程序使用指定的顶点属性索引
            glEnableVertexAttribArray(aTextureCoordLocation);
            glVertexAttribPointer(aTextureCoordLocation, 2, GL_FLOAT, false, 16, mBuffer);

            // 使用数组绘制图形：1.绘制的图形类型；2.从顶点数组读取的起点；3.从顶点数组读取的数据长度
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }
    }


}

