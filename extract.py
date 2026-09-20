import json
import os

path = r'C:\Users\ZNS\.gemini\antigravity\brain\a4870e76-4cab-4abe-bd63-63cfa743f257\.system_generated\logs\transcript_full.jsonl'
with open(path, 'r', encoding='utf-8') as f:
    for line in f:
        data = json.loads(line)
        if 'tool_calls' in data:
            for call in data['tool_calls']:
                if call.get('name') == 'replace_file_content':
                    args = call.get('args', {})
                    if 'AppState.kt' in args.get('TargetFile', ''):
                        print('--- REPLACEMENT ---')
                        print(args.get('ReplacementContent'))
                        print('-------------------')
