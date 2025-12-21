#version 450 core

uniform sampler2D clouds;
uniform sampler2D dist;
uniform int cloud_subsampling;
uniform float cloud_step;
uniform int overlay_width;
uniform int overlay_height;

const vec2 vector01 = vec2(0.0, 1.0);

vec4 cloud_overlay(float depth)
{
  vec2 point = gl_FragCoord.xy / cloud_subsampling;
  vec2 texcoord = floor(point - 0.5) + 0.5;
  vec2 frac = fract(point - 0.5);
  vec2 texcoord00 = (texcoord + vector01.xx) / vec2(overlay_width, overlay_height);
  vec2 texcoord01 = (texcoord + vector01.yx) / vec2(overlay_width, overlay_height);
  vec2 texcoord10 = (texcoord + vector01.xy) / vec2(overlay_width, overlay_height);
  vec2 texcoord11 = (texcoord + vector01.yy) / vec2(overlay_width, overlay_height);
  vec4 value00 = texture(clouds, texcoord00);
  vec4 value01 = texture(clouds, texcoord01);
  vec4 value10 = texture(clouds, texcoord10);
  vec4 value11 = texture(clouds, texcoord11);
  float dist00 = texture(dist, texcoord00).r;
  float dist01 = texture(dist, texcoord01).r;
  float dist10 = texture(dist, texcoord10).r;
  float dist11 = texture(dist, texcoord11).r;
  float exponent00 = - (depth - dist00) * (depth - dist00) / (2 * cloud_step * cloud_step);
  float exponent01 = - (depth - dist01) * (depth - dist01) / (2 * cloud_step * cloud_step);
  float exponent10 = - (depth - dist10) * (depth - dist10) / (2 * cloud_step * cloud_step);
  float exponent11 = - (depth - dist11) * (depth - dist11) / (2 * cloud_step * cloud_step);
  // Need to subtract the minimum exponent to prevent numerical underflow.
  float min_exponent = min(exponent00, min(exponent01, min(exponent10, exponent11)));
  float weight00 = (1.0 - frac.x) * (1.0 - frac.y) * exp(exponent00 - min_exponent);
  float weight01 =         frac.x * (1.0 - frac.y) * exp(exponent01 - min_exponent);
  float weight10 = (1.0 - frac.x) *         frac.y * exp(exponent10 - min_exponent);
  float weight11 =         frac.x *         frac.y * exp(exponent11 - min_exponent);
  return (value00 * weight00 + value01 * weight01 + value10 * weight10 + value11 * weight11) /
    (weight00 + weight01 + weight10 + weight11);
}
