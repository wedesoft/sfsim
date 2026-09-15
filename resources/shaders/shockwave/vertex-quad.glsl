#version 450 core

in vec3 point;
in vec2 uv;

out vec2 uv_fragment;

void main()
{
  gl_Position = vec4(point, 1);
  uv_fragment = uv;
}
