#version 450 core

uniform float scale;
uniform float max_curvature_radius;
uniform int size;

in vec2 uv_fragment;

float depth_source(vec2 uv);
vec4 normal_source(vec2 uv);
float curvature(vec4 normal, float max_result);

layout (location = 0) out vec4 point;

void main()
{
  float depth = depth_source(uv_fragment);
  vec4 normal = normal_source(uv_fragment);
  float curvature_ = curvature(normal, max_curvature_radius * scale) / scale;
  point = vec4(gl_FragCoord.xy * scale, depth, curvature_);
}
