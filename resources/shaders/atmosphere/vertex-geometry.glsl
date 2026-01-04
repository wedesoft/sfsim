#version 450 core

uniform mat4 inverse_projection;

in vec2 ndc;

out VS_OUT
{
  vec3 direction;
} vs_out;

void main()
{
  vs_out.direction = (inverse_projection * vec4(ndc, 0.0, 1.0)).xyz;
  gl_Position = vec4(ndc, 0.0, 1.0);
}
