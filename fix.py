import os

path = r'app/src/main/java/com/aistudio/missioncontrol/pxytwe/ui/screens/CameraAccessScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix the duplicate braces
content = content.replace('''                            }
                        }

                        }
                    }
                }
            } else if ''', '''                            }
                        }
                    }
                }
            } else if ''')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
