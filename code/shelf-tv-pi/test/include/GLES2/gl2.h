// Test-only stand-in for the OpenGL ES 2.0 header: just what Shelf uses. The Pi build uses the real one.
#pragma once
#include <stddef.h>
#include <stdint.h>
typedef unsigned int GLenum, GLuint, GLbitfield; typedef int GLint, GLsizei; typedef float GLfloat, GLclampf; typedef unsigned char GLboolean, GLubyte;
typedef char GLchar; typedef ptrdiff_t GLsizeiptr, GLintptr; typedef void GLvoid;
#define GL_FALSE 0
#define GL_TRUE 1
#define GL_DEPTH_BUFFER_BIT 0x00000100
#define GL_COLOR_BUFFER_BIT 0x00004000
#define GL_POINTS 0x0000
#define GL_TRIANGLES 0x0004
#define GL_LESS 0x0201
#define GL_LEQUAL 0x0203
#define GL_SRC_ALPHA 0x0302
#define GL_ONE_MINUS_SRC_ALPHA 0x0303
#define GL_ONE 1
#define GL_FRONT 0x0404
#define GL_BACK 0x0405
#define GL_CULL_FACE 0x0B44
#define GL_DEPTH_TEST 0x0B71
#define GL_BLEND 0x0BE2
#define GL_SCISSOR_TEST 0x0C11
#define GL_UNPACK_ALIGNMENT 0x0CF5
#define GL_TEXTURE_2D 0x0DE1
#define GL_UNSIGNED_BYTE 0x1401
#define GL_UNSIGNED_SHORT 0x1403
#define GL_FLOAT 0x1406
#define GL_ALPHA 0x1906
#define GL_RGB 0x1907
#define GL_RGBA 0x1908
#define GL_RENDERER 0x1F01
#define GL_LINEAR 0x2601
#define GL_TEXTURE_MAG_FILTER 0x2800
#define GL_TEXTURE_MIN_FILTER 0x2801
#define GL_TEXTURE_WRAP_S 0x2802
#define GL_TEXTURE_WRAP_T 0x2803
#define GL_CLAMP_TO_EDGE 0x812F
#define GL_DEPTH_COMPONENT16 0x81A5
#define GL_TEXTURE0 0x84C0
#define GL_TEXTURE1 0x84C1
#define GL_ARRAY_BUFFER 0x8892
#define GL_ELEMENT_ARRAY_BUFFER 0x8893
#define GL_STATIC_DRAW 0x88E4
#define GL_FRAGMENT_SHADER 0x8B30
#define GL_VERTEX_SHADER 0x8B31
#define GL_COMPILE_STATUS 0x8B81
#define GL_LINK_STATUS 0x8B82
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#define GL_COLOR_ATTACHMENT0 0x8CE0
#define GL_DEPTH_ATTACHMENT 0x8D00
#define GL_FRAMEBUFFER 0x8D40
#define GL_RENDERBUFFER 0x8D41
GLuint glCreateShader(GLenum); void glShaderSource(GLuint, GLsizei, const GLchar *const *, const GLint *); void glCompileShader(GLuint);
void glGetShaderiv(GLuint, GLenum, GLint *); void glGetShaderInfoLog(GLuint, GLsizei, GLsizei *, GLchar *); GLuint glCreateProgram(void);
void glAttachShader(GLuint, GLuint); void glBindAttribLocation(GLuint, GLuint, const GLchar *); void glLinkProgram(GLuint);
void glGetProgramiv(GLuint, GLenum, GLint *); void glGetProgramInfoLog(GLuint, GLsizei, GLsizei *, GLchar *); GLint glGetUniformLocation(GLuint, const GLchar *);
void glGenBuffers(GLsizei, GLuint *); void glBindBuffer(GLenum, GLuint); void glBufferData(GLenum, GLsizeiptr, const void *, GLenum);
void glGenTextures(GLsizei, GLuint *); void glBindTexture(GLenum, GLuint); void glDeleteTextures(GLsizei, const GLuint *); void glPixelStorei(GLenum, GLint);
void glTexImage2D(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void *); void glTexParameteri(GLenum, GLenum, GLint);
void glViewport(GLint, GLint, GLsizei, GLsizei); void glClearColor(GLfloat, GLfloat, GLfloat, GLfloat); void glClear(GLbitfield); void glUseProgram(GLuint);
void glEnableVertexAttribArray(GLuint); void glDisableVertexAttribArray(GLuint); void glVertexAttribPointer(GLuint, GLint, GLenum, GLboolean, GLsizei, const void *);
void glUniformMatrix4fv(GLint, GLsizei, GLboolean, const GLfloat *); void glUniform3fv(GLint, GLsizei, const GLfloat *); void glUniform4fv(GLint, GLsizei, const GLfloat *);
void glUniform1f(GLint, GLfloat); void glUniform1i(GLint, GLint); void glUniform2f(GLint, GLfloat, GLfloat); void glUniform4f(GLint, GLfloat, GLfloat, GLfloat, GLfloat);
void glEnable(GLenum); void glDisable(GLenum); void glDepthFunc(GLenum); void glDepthMask(GLboolean); void glCullFace(GLenum); void glBlendFunc(GLenum, GLenum);
void glActiveTexture(GLenum); void glDrawElements(GLenum, GLsizei, GLenum, const void *); void glDrawArrays(GLenum, GLint, GLsizei); void glScissor(GLint, GLint, GLsizei, GLsizei);
const GLubyte *glGetString(GLenum); void glReadPixels(GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, void *); void glFinish(void);
void glGenFramebuffers(GLsizei, GLuint *); void glBindFramebuffer(GLenum, GLuint); void glFramebufferTexture2D(GLenum, GLenum, GLenum, GLuint, GLint);
void glGenRenderbuffers(GLsizei, GLuint *); void glBindRenderbuffer(GLenum, GLuint); void glRenderbufferStorage(GLenum, GLenum, GLsizei, GLsizei);
void glFramebufferRenderbuffer(GLenum, GLenum, GLenum, GLuint); GLenum glCheckFramebufferStatus(GLenum);
