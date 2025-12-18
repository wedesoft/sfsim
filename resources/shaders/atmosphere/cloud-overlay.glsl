#version 450 core

uniform sampler2D clouds;
uniform int window_width;
uniform int window_height;

vec4 cloud_overlay(float dist)
{
  return texture(clouds, gl_FragCoord.xy / vec2(window_width, window_height));
}
