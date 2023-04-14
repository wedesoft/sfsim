#version 410 core

uniform float <%= factor %>;

float <%= noise %>(vec3 point);

float <%= method-name %>(vec3 point)
{
  return <%= noise %>(point * <%= factor %>);
}
