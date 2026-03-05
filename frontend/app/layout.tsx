import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });

export const metadata: Metadata = {
    title: "ContextBridge — AI Memory Plane",
    description:
        "Preserve, query, and visualize AI coding-session context snapshots across IDE sessions.",
    keywords: ["AI", "MCP", "context", "LLM", "memory", "IDE"],
};

export default function RootLayout({
    children,
}: {
    children: React.ReactNode;
}) {
    return (
        <html lang="en" className="dark" suppressHydrationWarning>
            <body
                className={`${inter.variable} font-sans antialiased bg-background text-foreground min-h-screen`}
            >
                {children}
            </body>
        </html>
    );
}
