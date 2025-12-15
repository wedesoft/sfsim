#version 450 core

uniform sampler2D dist;
uniform int overlay_width;
uniform int overlay_height;

float geometry_distance()
{
  vec2 uv = vec2(gl_FragCoord.x / overlay_width, gl_FragCoord.y / overlay_height);
  return texture(dist, uv).r;
}
