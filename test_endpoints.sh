#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}Testing MCP Server Endpoints${NC}\n"

# Test root endpoint
echo -e "${GREEN}1. Testing Root Endpoint (/)${NC}"
curl -s http://localhost:3000/ | python3 -m json.tool
echo -e "\n"

# Test overview endpoint
echo -e "${GREEN}2. Testing Overview Endpoint (/overview)${NC}"
curl -s http://localhost:3000/overview | python3 -m json.tool
echo -e "\n"

# Test speak DSL endpoints
echo -e "${GREEN}3. Testing Speak DSL Endpoints${NC}"

echo -e "${BLUE}Testing Header Endpoint (/header-speak-haxe)${NC}"
curl -s http://localhost:3000/header-speak-haxe | python3 -m json.tool
echo -e "\n"

echo -e "${BLUE}Testing Compile Endpoint (/compile-speak-haxe)${NC}"
curl -s -X POST http://localhost:3000/compile-speak-haxe \
     -H "Content-Type: application/json" \
     -d '{"dsl": "EnglishSpeaker says Hello, world!"}' | python3 -m json.tool
echo -e "\n"

echo -e "${BLUE}Testing Eyeball Endpoint (/eyeball-speak-haxe)${NC}"
curl -s -X POST http://localhost:3000/eyeball-speak-haxe \
     -H "Content-Type: application/json" \
     -d '{"code": "class EnglishSpeaker implements Speaker { public function speak():String { return \"Hello, world!\"; } }"}' | python3 -m json.tool
echo -e "\n"

# Test UI DSL endpoints
echo -e "${GREEN}4. Testing UI DSL Endpoints${NC}"

echo -e "${BLUE}Testing Header Endpoint (/header-ui-jinja2)${NC}"
curl -s http://localhost:3000/header-ui-jinja2 | python3 -m json.tool
echo -e "\n"

echo -e "${BLUE}Testing Compile Endpoint (/compile-ui-jinja2)${NC}"
curl -s -X POST http://localhost:3000/compile-ui-jinja2 \
     -H "Content-Type: application/json" \
     -d '{"dsl": "< header main-content footer >"}' | python3 -m json.tool
echo -e "\n"

echo -e "${BLUE}Testing Eyeball Endpoint (/eyeball-ui-jinja2)${NC}"
curl -s -X POST http://localhost:3000/eyeball-ui-jinja2 \
     -H "Content-Type: application/json" \
     -d '{"code": "<div class=\"row\"><div id=\"header\">{{ header }}</div></div>"}' | python3 -m json.tool
echo -e "\n"

# Test prompt endpoints
echo -e "${GREEN}5. Testing Prompt Endpoints${NC}"

echo -e "${BLUE}Testing speak DSL compile prompt${NC}"
curl -s http://localhost:3000/prompts/compile-speak-haxe | python3 -m json.tool
echo -e "\n"

echo -e "${BLUE}Testing speak DSL header prompt${NC}"
curl -s http://localhost:3000/prompts/header-speak-haxe | python3 -m json.tool
echo -e "\n"

echo -e "${BLUE}Testing UI DSL compile prompt${NC}"
curl -s http://localhost:3000/prompts/compile-ui-jinja2 | python3 -m json.tool
echo -e "\n"

echo -e "${BLUE}Testing UI DSL header prompt${NC}"
curl -s http://localhost:3000/prompts/header-ui-jinja2 | python3 -m json.tool
echo -e "\n" 