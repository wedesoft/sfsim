#version 450 core

layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;

in float colors[];

out float color;

void main(void)
{
  gl_Position = gl_in[0].gl_Position;
  color = colors[0];
  EmitVertex();
  gl_Position = gl_in[1].gl_Position;
  color = colors[1];
  EmitVertex();
  gl_Position = gl_in[2].gl_Position;
  color = colors[2];
  EmitVertex();
  EndPrimitive();
}
