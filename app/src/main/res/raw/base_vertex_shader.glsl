//顶点着色器
//vec4：4个分量的向量：x、y、z、w
attribute vec4 aPosition;

//为顶点着色程序添加矩阵 - 为视图投影矩阵创建一个变量，并将其作为着色程序位置的调节系数添加。
uniform mat4 uTextureMatrix;

//纹理坐标
attribute vec4 aTextureCoordinate;

//顶点着色器负责和片段着色器交流，所以我们需要创建一个变量和它共享相关的信息。在图像处理中，片段着色器需要的唯一相关信息就是顶点着色器现在正在处理哪个像素。
varying vec2 vTextureCoord;
void main()
{
    //取出这个顶点中纹理坐标的 X 和 Y 的位置。
    vTextureCoord = (uTextureMatrix * aTextureCoordinate).xy;
    //gl_Position：GL中默认定义的输出变量，决定了当前顶点的最终位置
    //直接把传入的坐标值作为输出传入渲染管线下一个阶段。gl_Position是OpenGL内置的变量
    gl_Position = aPosition;
}