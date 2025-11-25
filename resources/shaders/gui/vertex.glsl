#version 450 core

uniform mat4 projection;

in vec2 position;
in vec2 texcoord;
in vec4 color;

out vec2 frag_uv;
out vec4 frag_color;

void main()
{
  frag_uv = texcoord;
  frag_color = color;
  gl_Position = projection * vec4(position, 0, 1);
}
