#version 410 core

uniform samplerCube <%= current-name %>;
uniform int size;
uniform float scale;

layout (location = 0) out vec3 output1;
layout (location = 1) out vec3 output2;
layout (location = 2) out vec3 output3;
layout (location = 3) out vec3 output4;
layout (location = 4) out vec3 output5;
layout (location = 5) out vec3 output6;

vec3 <%= field-method-name %>(vec3 point);
vec3 interpolate_vector_cubemap(samplerCube cube, int size, vec3 idx);
vec3 face1_vector(vec2 texcoord);
vec3 face2_vector(vec2 texcoord);
vec3 face3_vector(vec2 texcoord);
vec3 face4_vector(vec2 texcoord);
vec3 face5_vector(vec2 texcoord);
vec3 face6_vector(vec2 texcoord);

vec3 update(vec3 v)
{
  vec3 idx = interpolate_vector_cubemap(<%= current-name %>, size, v);
  vec3 previous = normalize(idx);
  vec3 change = <%= field-method-name %>(previous);
  return previous + scale * change;
}

void main()
{
  vec2 x = (gl_FragCoord.xy - 0.5) / (size - 1);
  output1 = update(face1_vector(x));
  output2 = update(face2_vector(x));
  output3 = update(face3_vector(x));
  output4 = update(face4_vector(x));
  output5 = update(face5_vector(x));
  output6 = update(face6_vector(x));
}
