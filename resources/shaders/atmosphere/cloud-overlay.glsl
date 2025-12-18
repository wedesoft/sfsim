#version 450 core

uniform sampler2D clouds;
uniform sampler2D dist;
uniform int window_width;
uniform int window_height;

const vec2 vector01 = vec2(0.0, 1.0);

vec4 cloud_overlay(float depth)
{
  return texture(clouds, gl_FragCoord.xy / vec2(window_width, window_height));
}
