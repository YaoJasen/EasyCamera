//片段着色器
#extension GL_OES_EGL_image_external : require

// 定义所有浮点数据类型的默认精度；有lowp、mediump、highp 三种，但只有部分硬件支持片段着色器使用highp。(顶点着色器默认highp)
precision mediump float;
uniform samplerExternalOES uTextureSampler;
varying vec2 vTextureCoord;

void main()
{
    vec4 vCameraColor = texture2D(uTextureSampler, vTextureCoord);
    float fGrayColor = (0.3*vCameraColor.r + 0.59*vCameraColor.g + 0.11*vCameraColor.b);
    // gl_FragColor：GL中默认定义的输出变量，决定了当前片段的最终颜色
    gl_FragColor = vec4(fGrayColor, fGrayColor, fGrayColor, 1.0);
}