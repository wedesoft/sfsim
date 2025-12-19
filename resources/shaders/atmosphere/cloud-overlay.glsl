#version 450 core

uniform sampler2D clouds;
uniform sampler2D dist;
uniform int cloud_subsampling;
uniform int overlay_width;
uniform int overlay_height;

const vec2 vector01 = vec2(0.0, 1.0);

vec4 cloud_overlay(float depth)
{
  vec2 point = gl_FragCoord.xy / cloud_subsampling;
  vec2 texcoord = floor(point - 0.5) + 0.5;
  vec2 fractional = fract(point - 0.5);
  vec4 value00 = texture(clouds, (texcoord + vector01.xx) / vec2(overlay_width, overlay_height));
  vec4 value01 = texture(clouds, (texcoord + vector01.yx) / vec2(overlay_width, overlay_height));
  vec4 value10 = texture(clouds, (texcoord + vector01.xy) / vec2(overlay_width, overlay_height));
  vec4 value11 = texture(clouds, (texcoord + vector01.yy) / vec2(overlay_width, overlay_height));
  return mix(mix(value00, value01, fractional.x), mix(value10, value11, fractional.x), fractional.y);
}
