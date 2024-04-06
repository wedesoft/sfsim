#version 410 core

uniform mat4 shadow_ndc_to_world;
uniform int shadow_size;
in vec2 point;
out VS_OUT
{
  vec3 origin;
} vs_out;

vec4 grow_shadow_index(vec4 idx, int size_y, int size_x);

void main()
{
  gl_Position = vec4(point, 0, 1);
  vs_out.origin = (shadow_ndc_to_world * grow_shadow_index(vec4(point, 1, 1), shadow_size, shadow_size)).xyz;
}
